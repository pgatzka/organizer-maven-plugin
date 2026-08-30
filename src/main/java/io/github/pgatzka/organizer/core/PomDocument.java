package io.github.pgatzka.organizer.core;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * A {@code pom.xml} loaded for editing, preserving everything the editor does not touch.
 *
 * <p>Only the document element is re-serialized on {@link #render()}; the XML declaration, the
 * DOCTYPE, leading and trailing comments and the trailing newline are carried over from the
 * original text verbatim. The original character encoding, byte-order mark and line separator are
 * restored on write, so an unmodified document round-trips byte for byte.
 */
public final class PomDocument {

    private static final Pattern ENCODING = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern FIRST_INDENT = Pattern.compile("\\r?\\n([ \\t]+)<[^/!?]");
    private static final String UTF8_BOM = "\uFEFF";

    private final Document document;
    private final Path path;
    private final Charset charset;
    private final boolean byteOrderMark;
    private final String lineSeparator;
    private final String indentUnit;
    private final String prolog;
    private final String epilog;
    private final String startTag;
    private final String originalText;

    private PomDocument(
            Document document,
            Path path,
            Charset charset,
            boolean byteOrderMark,
            String lineSeparator,
            String indentUnit,
            String prolog,
            String epilog,
            String startTag,
            String originalText) {
        this.document = document;
        this.path = path;
        this.charset = charset;
        this.byteOrderMark = byteOrderMark;
        this.lineSeparator = lineSeparator;
        this.indentUnit = indentUnit;
        this.prolog = prolog;
        this.epilog = epilog;
        this.startTag = startTag;
        this.originalText = originalText;
    }

    /** Reads a POM from disk. */
    public static PomDocument load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return parse(decode(bytes), path);
    }

    /** Parses a POM held in memory; {@code path} may be {@code null} for in-memory documents. */
    public static PomDocument parse(String text, Path path) {
        boolean bom = text.startsWith(UTF8_BOM);
        String body = bom ? text.substring(UTF8_BOM.length()) : text;

        Charset charset = detectCharset(body);
        String lineSeparator = body.contains("\r\n") ? "\r\n" : "\n";
        String indentUnit = detectIndentUnit(body);

        // Parse first, so malformed input is reported by the XML parser rather than by the scanner.
        Document document = build(body);

        XmlBoundaries boundaries = XmlBoundaries.of(body);
        String prolog = body.substring(0, boundaries.start());
        String epilog = body.substring(boundaries.end());
        int startTagEnd = XmlBoundaries.endOfTag(body, boundaries.start());
        String startTag = startTagEnd < 0 ? null : body.substring(boundaries.start(), startTagEnd);

        return new PomDocument(
                document, path, charset, bom, lineSeparator, indentUnit, prolog, epilog, startTag, text);
    }

    private static String decode(byte[] bytes) {
        // Peek at the declaration using ISO-8859-1 so any single-byte encoding is readable.
        String peek = new String(bytes, StandardCharsets.ISO_8859_1);
        boolean bom = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
        Charset charset = detectCharset(bom ? peek.substring(3) : peek);
        String text = new String(bytes, charset);
        if (bom && !text.startsWith(UTF8_BOM)) {
            text = UTF8_BOM + text;
        }
        return text;
    }

    private static Document build(String body) {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setExpandEntities(false);
        setFeatureQuietly(builder, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeatureQuietly(builder, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(builder, "http://xml.org/sax/features/external-parameter-entities", false);
        try {
            return builder.build(new StringReader(body));
        } catch (JDOMException e) {
            throw new IllegalArgumentException("Not a well-formed XML document: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setFeatureQuietly(SAXBuilder builder, String feature, boolean value) {
        try {
            builder.setFeature(feature, value);
        } catch (RuntimeException e) {
            // Parser does not know the feature; the non-validating reader is safe enough.
        }
    }

    private static Charset detectCharset(String body) {
        if (body.startsWith("<?xml")) {
            int end = body.indexOf("?>");
            if (end > 0) {
                Matcher matcher = ENCODING.matcher(body.substring(0, end));
                if (matcher.find()) {
                    try {
                        return Charset.forName(matcher.group(1));
                    } catch (RuntimeException e) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String detectIndentUnit(String body) {
        Matcher matcher = FIRST_INDENT.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "  ";
    }

    /** The document element, normally {@code <project>}. */
    public Element getRoot() {
        return document.getRootElement();
    }

    public Document getDocument() {
        return document;
    }

    /** The POM namespace, or {@link Namespace#NO_NAMESPACE} for a namespace-less POM. */
    public Namespace getNamespace() {
        return document.getRootElement().getNamespace();
    }

    /** One level of indentation, as used by the document itself. */
    public String getIndentUnit() {
        return indentUnit;
    }

    public Path getPath() {
        return path;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    /** The file content as it was read. */
    public String getOriginalText() {
        return originalText;
    }

    /** Serializes the current state of the document. */
    public String render() {
        Format format = Format.getRawFormat();
        format.setLineSeparator(org.jdom2.output.LineSeparator.NL);
        format.setOmitDeclaration(true);
        format.setEncoding(charset.name());
        String body = new XMLOutputter(format).outputString(document.getRootElement());
        body = restoreStartTag(body);
        if ("\r\n".equals(lineSeparator)) {
            body = body.replace("\r\n", "\n").replace("\n", "\r\n");
        }
        StringBuilder out = new StringBuilder();
        if (byteOrderMark) {
            out.append(UTF8_BOM);
        }
        return out.append(prolog).append(body).append(epilog).toString();
    }

    /**
     * Restores the document element's original start tag.
     *
     * <p>A serializer writes all attributes on one line; POMs conventionally break the {@code xmlns}
     * declarations across three. Since no goal touches the attributes of {@code <project>}, the
     * original tag can simply be put back — but only when it still says the same thing, so a future
     * edit to those attributes is not silently discarded.
     */
    private String restoreStartTag(String rendered) {
        if (startTag == null) {
            return rendered;
        }
        int end = XmlBoundaries.endOfTag(rendered, 0);
        if (end < 0) {
            return rendered;
        }
        String renderedStartTag = rendered.substring(0, end);
        if (!collapseWhitespace(renderedStartTag).equals(collapseWhitespace(startTag))) {
            return rendered;
        }
        return startTag + rendered.substring(end);
    }

    /**
     * Normalizes a start tag for comparison: runs of whitespace become one space, and the optional
     * space a serializer puts before {@code />} is dropped, so {@code <project/>} and
     * {@code <project />} count as the same tag.
     */
    private static String collapseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").replaceAll("\\s+/>$", "/>");
    }

    /** Whether {@link #render()} would produce something other than the original text. */
    public boolean isModified() {
        return !render().equals(originalText);
    }

    /** Writes the current state back to {@code path}. */
    public void write() throws IOException {
        write(path);
    }

    /** Writes the current state to an arbitrary location. */
    public void write(Path target) throws IOException {
        if (target == null) {
            throw new IllegalStateException("This POM has no file to write to");
        }
        Files.writeString(target, render(), charset);
    }
}
