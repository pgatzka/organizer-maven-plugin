package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class OrganizeMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private OrganizeMojo mojo(String fixture) {
        pom(fixture);
        OrganizeMojo mojo = configure(new OrganizeMojo());
        mojo.setLog(log);
        mojo.sortDependencies = "groupId,artifactId";
        return mojo;
    }

    @Test
    void putsSectionsInSchemaOrder() throws Exception {
        mojo("messy.xml").execute();

        assertThat(content())
                .containsSubsequence(
                        "<modelVersion>", "<groupId>com.example</groupId>", "<artifactId>demo</artifactId>",
                        "<version>1.0.0</version>", "<properties>", "<dependencies>");
    }

    @Test
    void sortsDependenciesByCoordinate() throws Exception {
        mojo("messy.xml").execute();

        assertThat(content()).containsSubsequence("commons-lang3", "junit-jupiter");
    }

    @Test
    void putsTheChildrenOfADependencyInSchemaOrder() throws Exception {
        mojo("messy.xml").execute();

        assertThat(content())
                .contains("    <dependency>\n"
                        + "      <groupId>org.junit.jupiter</groupId>\n"
                        + "      <artifactId>junit-jupiter</artifactId>\n"
                        + "      <version>5.11.4</version>\n"
                        + "      <scope>test</scope>\n"
                        + "    </dependency>");
    }

    @Test
    void sortsProperties() throws Exception {
        mojo("messy.xml").execute();

        assertThat(content())
                .contains("  <properties>\n"
                        + "    <maven.compiler.release>17</maven.compiler.release>\n"
                        + "    <spring.version>6.1.0</spring.version>\n"
                        + "  </properties>");
    }

    @Test
    void aCommentTravelsWithTheElementBelowIt() throws Exception {
        mojo("messy.xml").execute();

        assertThat(content())
                .contains("    <!-- the string utilities -->\n"
                        + "    <dependency>\n"
                        + "      <groupId>org.apache.commons</groupId>");
    }

    @Test
    void isIdempotent() throws Exception {
        mojo("messy.xml").execute();
        String afterFirst = content();

        OrganizeMojo second = configure(new OrganizeMojo());
        second.setLog(log);
        second.sortDependencies = "groupId,artifactId";
        second.execute();

        assertThat(content()).isEqualTo(afterFirst);
        assertThat(log.text()).contains("No changes");
    }

    @Test
    void leavesAnOrganizedPomAlone() throws Exception {
        OrganizeMojo mojo = mojo("sample.xml");
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void keepsCommentsOutsideTheDocumentElement() throws Exception {
        mojo("sample.xml").execute();

        assertThat(content())
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- Top level comment")
                .endsWith("<!-- trailing comment -->\n");
    }

    @Test
    void sortsByAnyDependencyField() throws Exception {
        pomText("<project>\n  <dependencies>\n"
                + "    <dependency>\n      <groupId>a</groupId>\n      <artifactId>one</artifactId>\n"
                + "      <scope>test</scope>\n    </dependency>\n"
                + "    <dependency>\n      <groupId>b</groupId>\n      <artifactId>two</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());
        mojo.sortDependencies = "scope,groupId";

        mojo.execute();

        assertThat(content()).containsSubsequence("<groupId>b</groupId>", "<groupId>a</groupId>");
    }

    @Test
    void leavesDependenciesInPlaceWhenSortingIsOff() throws Exception {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.sortDependencies = "false";

        mojo.execute();

        assertThat(content()).containsSubsequence("junit-jupiter", "commons-lang3");
    }

    @Test
    void rejectsAnUnknownSortKey() {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.sortDependencies = "colour";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Cannot sort dependencies by 'colour'");
    }

    @Test
    void leavesPropertiesInPlaceWhenSortingIsOff() throws Exception {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.sortProperties = false;

        mojo.execute();

        assertThat(content()).containsSubsequence("spring.version", "maven.compiler.release");
    }

    @Test
    void sortsModules() throws Exception {
        pomText("<project>\n  <modules>\n    <module>zeta</module>\n    <module>alpha</module>\n"
                + "  </modules>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content()).contains("    <module>alpha</module>\n    <module>zeta</module>");
    }

    @Test
    void leavesModulesInPlaceWhenSortingIsOff() throws Exception {
        pomText("<project>\n  <modules>\n    <module>zeta</module>\n    <module>alpha</module>\n"
                + "  </modules>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());
        mojo.sortModules = false;

        mojo.execute();

        assertThat(content()).contains("    <module>zeta</module>\n    <module>alpha</module>");
    }

    @Test
    void sortsPlugins() throws Exception {
        pomText("<project>\n  <build>\n    <plugins>\n"
                + "      <plugin>\n        <artifactId>maven-surefire-plugin</artifactId>\n      </plugin>\n"
                + "      <plugin>\n        <artifactId>maven-jar-plugin</artifactId>\n      </plugin>\n"
                + "    </plugins>\n  </build>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content()).containsSubsequence("maven-jar-plugin", "maven-surefire-plugin");
    }

    @Test
    void neverReordersPluginConfiguration() throws Exception {
        pomText("<project>\n  <build>\n    <plugins>\n      <plugin>\n"
                + "        <artifactId>maven-surefire-plugin</artifactId>\n"
                + "        <configuration>\n"
                + "          <zebra>1</zebra>\n"
                + "          <apple>2</apple>\n"
                + "        </configuration>\n"
                + "      </plugin>\n    </plugins>\n  </build>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content()).contains("          <zebra>1</zebra>\n          <apple>2</apple>");
    }

    @Test
    void organizesInsideProfiles() throws Exception {
        pomText("<project>\n  <profiles>\n    <profile>\n"
                + "      <dependencies>\n        <dependency>\n"
                + "          <artifactId>a</artifactId>\n          <groupId>g</groupId>\n"
                + "        </dependency>\n      </dependencies>\n"
                + "      <id>ci</id>\n"
                + "    </profile>\n  </profiles>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content())
                .containsSubsequence("<id>ci</id>", "<dependencies>", "<groupId>g</groupId>", "<artifactId>a</artifactId>");
    }

    @Test
    void keepsSectionsInPlaceWhenReorderingIsOff() throws Exception {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.reorderSections = false;

        mojo.execute();

        assertThat(content()).containsSubsequence("<dependencies>", "<artifactId>demo</artifactId>");
    }

    @Test
    void dropsBlankLinesWhenAsked() throws Exception {
        pomText("<project>\n"
                + "  <artifactId>a</artifactId>\n\n"
                + "  <groupId>g</groupId>\n"
                + "</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());
        mojo.keepBlankLines = false;

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n  <groupId>g</groupId>\n  <artifactId>a</artifactId>\n</project>");
    }

    @Test
    void keepsBlankLinesByDefault() throws Exception {
        pomText("<project>\n"
                + "  <version>1</version>\n\n"
                + "  <artifactId>a</artifactId>\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <version>1</version>\n"
                        + "</project>");
    }

    @Test
    void dropsABlankLineOnABlockThatBecomesFirst() throws Exception {
        // A blank line belongs to the block below it, so it goes when that block moves to the top:
        // no POM wants an empty line straight after the opening tag.
        pomText("<project>\n"
                + "  <version>1</version>\n\n"
                + "  <artifactId>a</artifactId>\n"
                + "</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n  <artifactId>a</artifactId>\n  <version>1</version>\n</project>");
    }

    @Test
    void leavesMixedContentAlone() throws Exception {
        pomText("<project>\n  <name>text <b>and</b> markup</name>\n</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void leavesASectionEndingInACommentAlone() throws Exception {
        pomText("<project>\n"
                + "  <version>1</version>\n"
                + "  <artifactId>a</artifactId>\n"
                + "  <!-- what does this belong to? -->\n"
                + "</project>");
        OrganizeMojo mojo = configure(new OrganizeMojo());
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- check mode

    @Test
    void checkOnlyPassesOnAnOrganizedPom() throws Exception {
        OrganizeMojo mojo = mojo("sample.xml");
        mojo.checkOnly = true;

        mojo.execute();

        assertThat(log.text()).contains("is organized");
    }

    @Test
    void checkOnlyFailsOnAnUnorganizedPom() {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.checkOnly = true;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("is not organized");
    }

    @Test
    void checkOnlyPrintsTheDiffAndWritesNothing() {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.checkOnly = true;
        String before = content();

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("+  <modelVersion>4.0.0</modelVersion>");
    }

    @Test
    void aDryRunOrganizesNothingOnDisk() throws Exception {
        OrganizeMojo mojo = mojo("messy.xml");
        mojo.dryRun = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("Dry run");
    }
}
