package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class XmlBoundariesTest {

    @Test
    void findsTheDocumentElementAfterADeclaration() {
        String xml = "<?xml version=\"1.0\"?>\n<project></project>\n";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(xml.substring(boundaries.start(), boundaries.end())).isEqualTo("<project></project>");
    }

    @Test
    void skipsLeadingCommentsAndProcessingInstructions() {
        String xml = "<?xml version=\"1.0\"?>\n<!-- <project> is not here -->\n<?php ?>\n<project/>\n";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(xml.substring(boundaries.start(), boundaries.end())).isEqualTo("<project/>");
    }

    @Test
    void skipsADoctypeWithAnInternalSubset() {
        String xml = "<!DOCTYPE project [ <!ENTITY x \"y\"> ]>\n<project></project>";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(boundaries.start()).isEqualTo(xml.indexOf("<project"));
    }

    @Test
    void ignoresTagsInsideCommentsAndCdata() {
        String xml = "<project><!-- </project> --><a><![CDATA[</project>]]></a></project>\ntrailing";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(xml.substring(boundaries.end())).isEqualTo("\ntrailing");
    }

    @Test
    void ignoresAngleBracketsInsideAttributeValues() {
        String xml = "<project title=\"a > b\"><a/></project>";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(boundaries.end()).isEqualTo(xml.length());
    }

    @Test
    void handlesASelfClosingDocumentElement() {
        String xml = "<project attr=\"1\"/>\n";

        XmlBoundaries boundaries = XmlBoundaries.of(xml);

        assertThat(xml.substring(boundaries.start(), boundaries.end())).isEqualTo("<project attr=\"1\"/>");
    }

    @Test
    void reportsTheEndOfAStartTag() {
        String xml = "<project a=\"1\"\n         b=\"2\">body</project>";

        assertThat(xml.substring(0, XmlBoundaries.endOfTag(xml, 0)))
                .isEqualTo("<project a=\"1\"\n         b=\"2\">");
    }

    @Test
    void rejectsInputWithoutADocumentElement() {
        assertThatThrownBy(() -> XmlBoundaries.of("<?xml version=\"1.0\"?>\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No document element");
    }

    @Test
    void rejectsAnUnclosedDocumentElement() {
        assertThatThrownBy(() -> XmlBoundaries.of("<project><a/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not closed");
    }

    @Test
    void rejectsContentBeforeTheDocumentElement() {
        assertThatThrownBy(() -> XmlBoundaries.of("garbage <project/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No document element");
    }
}
