package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.core.DependencyReport.Entry;
import io.github.pgatzka.organizer.core.DependencyReport.Format;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyReportTest {

    private static Entry entry(String coordinate, String scope, boolean managed) {
        return new Entry(Coordinate.parse(coordinate), scope, managed);
    }

    @Test
    void parsesFormatNames() {
        assertThat(Format.parse("plain")).isEqualTo(Format.PLAIN);
        assertThat(Format.parse(" TABLE ")).isEqualTo(Format.TABLE);
        assertThat(Format.parse(null)).isEqualTo(Format.PLAIN);
        assertThat(Format.parse("  ")).isEqualTo(Format.PLAIN);
    }

    @Test
    void rejectsUnknownFormatNames() {
        assertThatThrownBy(() -> Format.parse("csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plain, table or tree");
    }

    @Test
    void showsAPlaceholderForAnInheritedVersion() {
        assertThat(entry("g:a", "compile", false).displayVersion()).isEqualTo("(managed)");
        assertThat(entry("g:a:1.0", "compile", false).displayVersion()).isEqualTo("1.0");
    }

    @Test
    void reportsNothingToShowForAnEmptyList() {
        assertThat(DependencyReport.render(List.of(), Format.PLAIN)).containsExactly("No dependencies match.");
        assertThat(DependencyReport.render(List.of(), Format.TABLE)).containsExactly("No dependencies match.");
        assertThat(DependencyReport.render(List.of(), Format.TREE)).containsExactly("No dependencies match.");
    }

    @Test
    void alignsTableColumnsToTheWidestValue() {
        List<String> lines = DependencyReport.render(
                List.of(entry("a:b:1", "compile", false), entry("much.longer.group:artifact:2", "test", false)),
                Format.TABLE);

        assertThat(lines)
                .containsExactly(
                        "GROUP ID           ARTIFACT ID  VERSION  SCOPE",
                        "-----------------  -----------  -------  -----",
                        "a                  b            1        compile",
                        "much.longer.group  artifact     2        test");
    }

    @Test
    void marksManagedRowsInATable() {
        List<String> lines = DependencyReport.render(List.of(entry("a:b:1", "compile", true)), Format.TABLE);

        assertThat(lines.get(2)).endsWith("compile (managed)");
    }

    @Test
    void groupsTheTreeByScope() {
        List<String> lines = DependencyReport.render(
                List.of(
                        entry("a:b:1", "compile", false),
                        entry("c:d:2", "compile", false),
                        entry("e:f:3", "test", false)),
                Format.TREE);

        assertThat(lines).containsExactly("compile", "+- a:b:1", "\\- c:d:2", "test", "\\- e:f:3");
    }

    @Test
    void putsManagedEntriesInTheirOwnTreeGroup() {
        List<String> lines = DependencyReport.render(List.of(entry("a:b:1", "compile", true)), Format.TREE);

        assertThat(lines).containsExactly("dependencyManagement", "\\- a:b:1");
    }

    @Test
    void keepsEverythingWhenNoFilterIsGiven() {
        List<Entry> entries = List.of(entry("a:b:1", "compile", false), entry("c:d:2", "test", false));

        assertThat(DependencyReport.filter(entries, null, null)).hasSize(2);
        assertThat(DependencyReport.filter(entries, "  ", null)).hasSize(2);
    }

    @Test
    void filtersByScopeAndPattern() {
        List<Entry> entries = List.of(entry("a:b:1", "compile", false), entry("a:c:2", "test", false));

        assertThat(DependencyReport.filter(entries, "test", null)).hasSize(1);
        assertThat(DependencyReport.filter(entries, null, Coordinate.parse("a:b"))).hasSize(1);
        assertThat(DependencyReport.filter(entries, "test", Coordinate.parse("a:b"))).isEmpty();
    }

    @Test
    void treatsAMissingScopeAsCompile() {
        PomDocument pom = PomDocument.parse(
                "<project><dependencies><dependency><groupId>g</groupId>"
                        + "<artifactId>a</artifactId></dependency></dependencies></project>",
                null);

        assertThat(DependencyReport.collect(pom.getRoot(), false).get(0).scope()).isEqualTo("compile");
    }

    @Test
    void collectsManagedEntriesOnlyWhenAsked() {
        PomDocument pom = PomDocument.parse(
                "<project><dependencyManagement><dependencies><dependency><groupId>g</groupId>"
                        + "<artifactId>a</artifactId><version>1</version></dependency></dependencies>"
                        + "</dependencyManagement></project>",
                null);

        assertThat(DependencyReport.collect(pom.getRoot(), false)).isEmpty();
        assertThat(DependencyReport.collect(pom.getRoot(), true)).singleElement().satisfies(entry -> {
            assertThat(entry.managed()).isTrue();
            assertThat(entry.ga()).isEqualTo("g:a");
        });
    }
}
