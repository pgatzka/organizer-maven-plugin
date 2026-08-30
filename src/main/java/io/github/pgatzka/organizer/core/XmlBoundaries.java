package io.github.pgatzka.organizer.core;

/**
 * Locates the byte offsets of the document element inside a raw XML text.
 *
 * <p>Everything before the document element (XML declaration, DOCTYPE, leading comments and
 * processing instructions) and everything after it is kept verbatim by {@link PomDocument}, so that
 * only the part of the file that can actually change is re-serialized.
 */
final class XmlBoundaries {

    private final int start;
    private final int end;

    private XmlBoundaries(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /** Offset of the {@code <} that opens the document element. */
    int start() {
        return start;
    }

    /** Offset just past the {@code >} that closes the document element. */
    int end() {
        return end;
    }

    static XmlBoundaries of(String xml) {
        int start = findRootStart(xml);
        if (start < 0) {
            throw new IllegalArgumentException("No document element found in XML input");
        }
        int end = findRootEnd(xml, start);
        if (end < 0) {
            throw new IllegalArgumentException("Document element is not closed in XML input");
        }
        return new XmlBoundaries(start, end);
    }

    private static int findRootStart(String xml) {
        int i = 0;
        int n = xml.length();
        while (i < n) {
            char c = xml.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (xml.startsWith("<?", i)) {
                i = skipTo(xml, i, "?>");
            } else if (xml.startsWith("<!--", i)) {
                i = skipTo(xml, i, "-->");
            } else if (xml.startsWith("<!", i)) {
                i = skipDoctype(xml, i);
            } else if (c == '<') {
                return i;
            } else {
                // Stray content before the document element: not well-formed XML.
                return -1;
            }
            if (i < 0) {
                return -1;
            }
        }
        return -1;
    }

    private static int findRootEnd(String xml, int rootStart) {
        int depth = 0;
        int i = rootStart;
        int n = xml.length();
        while (i < n) {
            if (xml.charAt(i) != '<') {
                i++;
                continue;
            }
            if (xml.startsWith("<!--", i)) {
                i = skipTo(xml, i, "-->");
            } else if (xml.startsWith("<![CDATA[", i)) {
                i = skipTo(xml, i, "]]>");
            } else if (xml.startsWith("<?", i)) {
                i = skipTo(xml, i, "?>");
            } else if (xml.startsWith("<!", i)) {
                i = skipDoctype(xml, i);
            } else if (xml.startsWith("</", i)) {
                i = skipTag(xml, i);
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else {
                int tagEnd = skipTag(xml, i);
                if (tagEnd < 0) {
                    return -1;
                }
                boolean selfClosing = xml.charAt(tagEnd - 2) == '/';
                if (!selfClosing) {
                    depth++;
                } else if (depth == 0) {
                    return tagEnd;
                }
                i = tagEnd;
            }
            if (i < 0) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Returns the offset just past the {@code >} that closes the tag starting at {@code from},
     * honouring quoted attribute values. Returns {@code -1} when the tag is unterminated.
     */
    static int endOfTag(String xml, int from) {
        return skipTag(xml, from);
    }

    /** Skips a start or end tag, honouring quoted attribute values. Returns the offset past {@code >}. */
    private static int skipTag(String xml, int from) {
        int i = from + 1;
        int n = xml.length();
        char quote = 0;
        while (i < n) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    /** Skips {@code <!DOCTYPE ...>}, including an internal subset. */
    private static int skipDoctype(String xml, int from) {
        int i = from + 2;
        int n = xml.length();
        char quote = 0;
        while (i < n) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                int close = xml.indexOf(']', i);
                if (close < 0) {
                    return -1;
                }
                i = close;
            } else if (c == '>') {
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    private static int skipTo(String xml, int from, String terminator) {
        int idx = xml.indexOf(terminator, from);
        return idx < 0 ? -1 : idx + terminator.length();
    }
}
