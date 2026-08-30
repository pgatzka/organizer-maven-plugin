package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.core.Plugins.Execution;
import org.jdom2.Element;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginsTest {

    @Test
    void defaultsTheGroupToMavensOwnPlugins() {
        Coordinate coordinate = Plugins.parseCoordinate("maven-surefire-plugin");

        assertThat(coordinate.getGroupId()).isEqualTo("org.apache.maven.plugins");
        assertThat(coordinate.getArtifactId()).isEqualTo("maven-surefire-plugin");
        assertThat(coordinate.hasVersion()).isFalse();
    }

    @Test
    void readsTwoSegmentsAsArtifactAndVersionWhenTheSecondIsAVersion() {
        Coordinate coordinate = Plugins.parseCoordinate("maven-surefire-plugin:3.5.2");

        assertThat(coordinate.getGroupId()).isEqualTo("org.apache.maven.plugins");
        assertThat(coordinate.getArtifactId()).isEqualTo("maven-surefire-plugin");
        assertThat(coordinate.getVersion()).isEqualTo("3.5.2");
    }

    @Test
    void readsTwoSegmentsAsGroupAndArtifactWhenTheFirstIsAGroup() {
        Coordinate coordinate = Plugins.parseCoordinate("org.jacoco:jacoco-maven-plugin");

        assertThat(coordinate.getGroupId()).isEqualTo("org.jacoco");
        assertThat(coordinate.getArtifactId()).isEqualTo("jacoco-maven-plugin");
        assertThat(coordinate.hasVersion()).isFalse();
    }

    @Test
    void readsTwoSegmentsAsGroupAndArtifactWhenTheSecondIsNotAVersion() {
        assertThat(Plugins.parseCoordinate("mygroup:myplugin").getArtifactId()).isEqualTo("myplugin");
    }

    @Test
    void keepsAnExplicitGroup() {
        assertThat(Plugins.parseCoordinate("org.jacoco:jacoco-maven-plugin:0.8.12").getGroupId())
                .isEqualTo("org.jacoco");
    }

    @Test
    void rejectsAnEmptyPluginCoordinate() {
        assertThatThrownBy(() -> Plugins.parseCoordinate(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing plugin");
    }

    @Test
    void readsTheCoordinateOfADeclaredPlugin() {
        PomDocument pom = PomDocument.parse(
                "<project><build><plugins><plugin><artifactId>maven-jar-plugin</artifactId>"
                        + "<version>3.4.2</version></plugin></plugins></build></project>",
                null);
        Element plugins = Poms.path(pom.getRoot(), "build", "plugins").orElseThrow();

        assertThat(Plugins.coordinateOf(Poms.children(plugins, "plugin").get(0)).toString())
                .isEqualTo("org.apache.maven.plugins:maven-jar-plugin:3.4.2");
    }

    @Test
    void parsesConfigurationPairs() {
        assertThat(Plugins.parseConfiguration("skipTests=true, argLine=-Xmx1g"))
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("skipTests", "true"),
                        org.assertj.core.api.Assertions.entry("argLine", "-Xmx1g"));
    }

    @Test
    void acceptsAValueContainingAnEqualsSign() {
        assertThat(Plugins.parseConfiguration("argLine=-Dkey=value"))
                .containsEntry("argLine", "-Dkey=value");
    }

    @Test
    void treatsNoConfigurationAsEmpty() {
        assertThat(Plugins.parseConfiguration(null)).isEmpty();
        assertThat(Plugins.parseConfiguration("  ")).isEmpty();
    }

    @Test
    void rejectsAConfigurationEntryWithoutAValue() {
        assertThatThrownBy(() -> Plugins.parseConfiguration("justAKey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected key=value");
    }

    @Test
    void parsesAnExecution() {
        Execution execution = Execution.parse("make-jar:package:jar+test-jar");

        assertThat(execution.id()).isEqualTo("make-jar");
        assertThat(execution.phase()).isEqualTo("package");
        assertThat(execution.goals()).containsExactly("jar", "test-jar");
    }

    @Test
    void allowsAnExecutionWithOnlyAnId() {
        assertThat(Execution.parse("just-an-id").phase()).isNull();
    }

    @Test
    void allowsAnExecutionWithoutAnId() {
        Execution execution = Execution.parse(":package:jar");

        assertThat(execution.id()).isNull();
        assertThat(execution.phase()).isEqualTo("package");
    }

    @Test
    void rejectsAnEmptyOrOverlongExecution() {
        assertThatThrownBy(() -> Execution.parse("::")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execution.parse("a:b:c:d")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSeveralExecutions() {
        assertThat(Execution.parseAll("one:package:jar,two:verify:test")).hasSize(2);
        assertThat(Execution.parseAll(null)).isEmpty();
    }

    @Test
    void leavesOutTheDefaultGroupWhenBuildingAnEntry() {
        PomDocument pom = PomDocument.parse("<project/>", null);

        Element built = Plugins.build(
                pom.getRoot(), Plugins.parseCoordinate("maven-jar-plugin:3.4.2"), java.util.Map.of(), List.of());

        assertThat(Poms.child(built, "groupId")).isNull();
        assertThat(Poms.childText(built, "artifactId")).isEqualTo("maven-jar-plugin");
    }

    @Test
    void writesANonDefaultGroup() {
        PomDocument pom = PomDocument.parse("<project/>", null);

        Element built = Plugins.build(
                pom.getRoot(), Plugins.parseCoordinate("org.jacoco:jacoco-maven-plugin:0.8.12"),
                java.util.Map.of(), List.of());

        assertThat(Poms.childText(built, "groupId")).isEqualTo("org.jacoco");
    }
}
