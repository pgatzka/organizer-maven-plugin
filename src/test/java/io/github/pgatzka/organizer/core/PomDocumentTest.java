package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.Fixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PomDocumentTest {

    @ParameterizedTest
    @ValueSource(strings = {"sample.xml", "no-namespace.xml", "minimal.xml"})
    void rendersUnchangedDocumentByteForByte(String fixture) {
        String original = Fixtures.text(fixture);
        PomDocument pom = PomDocument.parse(original, null);

        assertThat(pom.render()).isEqualTo(original);
        assertThat(pom.isModified()).isFalse();
    }

    @Test
    void preservesCrlfLineEndings() {
        String original = Fixtures.text("sample.xml").replace("\n", "\r\n");
        PomDocument pom = PomDocument.parse(original, null);

        assertThat(pom.getLineSeparator()).isEqualTo("\r\n");
        assertThat(pom.render()).isEqualTo(original);
    }

    @Test
    void preservesByteOrderMark() {
        String original = "﻿" + Fixtures.text("minimal.xml");
        PomDocument pom = PomDocument.parse(original, null);

        assertThat(pom.render()).isEqualTo(original);
    }

    @Test
    void keepsCommentsBeforeAndAfterTheDocumentElement() {
        PomDocument pom = PomDocument.parse(Fixtures.text("sample.xml"), null);

        assertThat(pom.render()).contains("<!-- Top level comment, kept verbatim -->");
        assertThat(pom.render()).endsWith("<!-- trailing comment -->\n");
    }

    @Test
    void detectsTwoSpaceIndentation() {
        assertThat(PomDocument.parse(Fixtures.text("sample.xml"), null).getIndentUnit()).isEqualTo("  ");
    }

    @Test
    void detectsTabIndentation() {
        assertThat(PomDocument.parse(Fixtures.text("no-namespace.xml"), null).getIndentUnit())
                .isEqualTo("\t");
    }

    @Test
    void fallsBackToTwoSpacesForASingleLineDocument() {
        PomDocument pom = PomDocument.parse("<project><modelVersion>4.0.0</modelVersion></project>", null);

        assertThat(pom.getIndentUnit()).isEqualTo("  ");
    }

    @Test
    void exposesTheNamespaceOfTheDocument() {
        assertThat(PomDocument.parse(Fixtures.text("sample.xml"), null).getNamespace().getURI())
                .isEqualTo("http://maven.apache.org/POM/4.0.0");
        assertThat(PomDocument.parse(Fixtures.text("no-namespace.xml"), null).getNamespace().getURI())
                .isEmpty();
    }

    @Test
    void reportsModificationAfterAnEdit() {
        PomDocument pom = PomDocument.parse(Fixtures.text("minimal.xml"), null);
        Poms.setChildText(pom.getRoot(), "version", "0.2.0", pom.getIndentUnit());

        assertThat(pom.isModified()).isTrue();
        assertThat(pom.render()).contains("<version>0.2.0</version>");
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws IOException {
        Path path = Fixtures.copyTo(dir, "sample.xml");
        PomDocument pom = PomDocument.load(path);

        pom.write();

        assertThat(Fixtures.read(path)).isEqualTo(Fixtures.text("sample.xml"));
    }

    @Test
    void writesUsingTheDeclaredEncoding(@TempDir Path dir) throws IOException {
        String original = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<project>\n  <name>café</name>\n</project>\n";
        Path path = dir.resolve("pom.xml");
        Files.write(path, original.getBytes(StandardCharsets.ISO_8859_1));

        PomDocument pom = PomDocument.load(path);
        assertThat(pom.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        pom.write();

        assertThat(Files.readAllBytes(path))
                .isEqualTo(original.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void roundTripsAnEmptyDocumentElement() {
        assertThat(PomDocument.parse("<project/>\n", null).render()).isEqualTo("<project/>\n");
        assertThat(PomDocument.parse("<project />\n", null).render()).isEqualTo("<project />\n");
    }

    @Test
    void fallsBackToUtf8ForAnUnknownEncoding() {
        String original = "<?xml version=\"1.0\" encoding=\"NOT-A-CHARSET\"?>\n<project/>\n";

        PomDocument pom = PomDocument.parse(original, null);

        assertThat(pom.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(pom.render()).isEqualTo(original);
    }

    @Test
    void fallsBackToUtf8ForADeclarationWithoutAnEncoding() {
        assertThat(PomDocument.parse("<?xml version=\"1.0\"?>\n<project/>\n", null).getCharset())
                .isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void fallsBackToUtf8WithoutADeclaration() {
        assertThat(PomDocument.parse("<project/>\n", null).getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void readsAByteOrderMarkFromDisk(@TempDir Path dir) throws IOException {
        String original = "\uFEFF<project/>\n";
        Path path = dir.resolve("pom.xml");
        Files.write(path, original.getBytes(StandardCharsets.UTF_8));

        PomDocument pom = PomDocument.load(path);
        pom.write();

        assertThat(Files.readAllBytes(path)).isEqualTo(original.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exposesThePathItWasLoadedFrom(@TempDir Path dir) throws IOException {
        Path path = Fixtures.copyTo(dir, "minimal.xml");

        assertThat(PomDocument.load(path).getPath()).isEqualTo(path);
    }

    @Test
    void writesToAnotherLocation(@TempDir Path dir) throws IOException {
        PomDocument pom = PomDocument.parse(Fixtures.text("minimal.xml"), null);
        Path elsewhere = dir.resolve("copy.xml");

        pom.write(elsewhere);

        assertThat(Fixtures.read(elsewhere)).isEqualTo(Fixtures.text("minimal.xml"));
    }

    @Test
    void handlesADocumentWithADoctype() {
        String original = "<!DOCTYPE project>\n<project>\n  <name>x</name>\n</project>\n";

        assertThat(PomDocument.parse(original, null).render()).isEqualTo(original);
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> PomDocument.parse("<project><oops></project>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("well-formed");
    }

    @Test
    void rejectsInputWithoutADocumentElement() {
        assertThatThrownBy(() -> PomDocument.parse("<?xml version=\"1.0\"?>\n", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("well-formed");
    }

    @Test
    void keepsTheMultiLineProjectStartTagAsWritten() {
        PomDocument pom = PomDocument.parse(Fixtures.text("sample.xml"), null);
        Poms.setChildText(pom.getRoot(), "version", "2.0.0", pom.getIndentUnit());

        assertThat(pom.render())
                .contains("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                        + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    }

    @Test
    void doesNotRestoreTheStartTagAfterAnAttributeChange() {
        PomDocument pom = PomDocument.parse(Fixtures.text("sample.xml"), null);
        pom.getRoot().setAttribute("id", "changed");

        assertThat(pom.render()).contains("id=\"changed\"");
    }

    @Test
    void writeWithoutAPathFails() {
        PomDocument pom = PomDocument.parse(Fixtures.text("minimal.xml"), null);

        assertThatThrownBy(pom::write).isInstanceOf(IllegalStateException.class);
    }
}
