package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.organizer.support.Fixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PomDiffTest {

    @Test
    void reportsNoDiffForIdenticalText() {
        assertThat(PomDiff.unified("same\n", "same\n", "pom.xml")).isEmpty();
    }

    @Test
    void showsAddedLines() {
        var diff = PomDiff.unified("a\nb\n", "a\nnew\nb\n", "pom.xml");

        assertThat(diff).contains("+new");
        assertThat(diff.get(0)).contains("pom.xml");
    }

    @Test
    void showsRemovedLines() {
        assertThat(PomDiff.unified("a\ngone\nb\n", "a\nb\n", "pom.xml")).contains("-gone");
    }

    @Test
    void diffsAPomAgainstItsOnDiskState() {
        PomDocument pom = PomDocument.parse(Fixtures.text("minimal.xml"), Path.of("some", "pom.xml"));
        Poms.setChildText(pom.getRoot(), "version", "9.9.9", pom.getIndentUnit());

        var diff = PomDiff.of(pom);

        assertThat(diff).anyMatch(line -> line.equals("+  <version>9.9.9</version>"));
        assertThat(diff).anyMatch(line -> line.equals("-  <version>0.1.0</version>"));
        assertThat(diff.get(0)).contains("pom.xml");
    }

    @Test
    void namesTheFilePomXmlWhenTheDocumentHasNoPath() {
        PomDocument pom = PomDocument.parse(Fixtures.text("minimal.xml"), null);
        Poms.setChildText(pom.getRoot(), "version", "9.9.9", pom.getIndentUnit());

        assertThat(PomDiff.of(pom).get(0)).contains("pom.xml");
    }

    @Test
    void reportsNoDiffForAnUntouchedPom() {
        assertThat(PomDiff.of(PomDocument.parse(Fixtures.text("minimal.xml"), null))).isEmpty();
    }
}
