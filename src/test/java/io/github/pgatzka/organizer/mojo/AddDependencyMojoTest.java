package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class AddDependencyMojoTest extends MojoTest {

    private AddDependencyMojo mojo() {
        pom("sample.xml");
        return configure(new AddDependencyMojo());
    }

    @Test
    void addsADependencyWithMatchingIndentation() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(content())
                .contains("    <dependency>\n"
                        + "      <groupId>com.acme</groupId>\n"
                        + "      <artifactId>widget</artifactId>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>");
    }

    @Test
    void leavesTheRestOfThePomAlone() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(content())
                .contains("<!-- pinned deliberately, see PLAT-411 -->")
                .contains("<!-- test scope below -->")
                .contains("<!-- trailing comment -->")
                .contains("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n         xmlns:xsi=");
    }

    @Test
    void writesTheScopeAndOptionalFlag() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.scope = "test";
        mojo.optional = true;

        mojo.execute();

        assertThat(content()).contains("<scope>test</scope>\n      <optional>true</optional>");
    }

    @Test
    void writesClassifierAndType() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0:tests:test-jar";

        mojo.execute();

        assertThat(content()).contains("<type>test-jar</type>").contains("<classifier>tests</classifier>");
    }

    @Test
    void writesExclusions() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.exclusions = "org.slf4j:slf4j-api, commons-logging:commons-logging";

        mojo.execute();

        assertThat(content())
                .contains("      <exclusions>\n"
                        + "        <exclusion>\n"
                        + "          <groupId>org.slf4j</groupId>\n"
                        + "          <artifactId>slf4j-api</artifactId>\n"
                        + "        </exclusion>\n"
                        + "        <exclusion>\n"
                        + "          <groupId>commons-logging</groupId>\n"
                        + "          <artifactId>commons-logging</artifactId>\n"
                        + "        </exclusion>\n"
                        + "      </exclusions>");
    }

    @Test
    void acceptsSeparateCoordinateParameters() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.groupId = "com.acme";
        mojo.artifactId = "widget";
        mojo.version = "1.0.0";

        mojo.execute();

        assertThat(content()).contains("<artifactId>widget</artifactId>");
    }

    @Test
    void individualParametersOverrideTheArtifactCoordinate() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.version = "2.0.0";

        mojo.execute();

        assertThat(content()).contains("<version>2.0.0</version>").doesNotContain("<version>1.0.0</version>");
    }

    @Test
    void createsTheDependenciesSectionWhenMissing() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>com.acme</groupId>\n"
                        + "      <artifactId>widget</artifactId>\n"
                        + "      <version>1.0.0</version>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>\n"
                        + "</project>");
    }

    @Test
    void updatesAnExistingDependencyInsteadOfDuplicatingIt() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3:3.17.0";

        mojo.execute();

        assertThat(content()).contains("<version>3.17.0</version>").doesNotContain("3.14.0");
        assertThat(content().split("commons-lang3", -1)).hasSize(2);
    }

    @Test
    void addsAScopeToAnExistingDependencyInSchemaOrder() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3";
        mojo.scope = "provided";

        mojo.execute();

        assertThat(content())
                .contains("      <version>3.14.0</version>\n      <scope>provided</scope>\n    </dependency>");
    }

    @Test
    void reportsNoChangeWhenTheDependencyIsAlreadyAsRequested() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3:3.14.0";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void failsOnAnExistingDependencyWhenAsked() {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3:3.17.0";
        mojo.failOnExisting = true;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("already declared");
    }

    @Test
    void omitsTheVersionWhenDependencyManagementProvidesIt() throws Exception {
        pomText("<project>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>com.acme</groupId>\n"
                + "        <artifactId>widget</artifactId>\n"
                + "        <version>9.9.9</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget";

        mojo.execute();

        assertThat(content())
                .contains("  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>com.acme</groupId>\n"
                        + "      <artifactId>widget</artifactId>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>");
    }

    @Test
    void failsWhenNoVersionIsGivenOrManaged() {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:widget";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No version given for com.acme:widget");
    }

    @Test
    void asksForTheCoordinateWhenNothingWasPassed() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);
        mojo.version = "1.0.0";

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("groupId", "artifactId");
        assertThat(content()).contains("<artifactId>widget</artifactId>");
    }

    @Test
    void failsWithAnActionableMessageInBatchMode() {
        AddDependencyMojo mojo = mojo();

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-DgroupId=<value>")
                .hasMessageContaining("run without -B");
    }

    @Test
    void failsWhenThePomIsMissing() {
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class)
                .hasMessageContaining("No POM found");
    }

    @Test
    void failsWhenThePomIsNotWellFormed() {
        pomText("<project><oops></project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class)
                .hasMessageContaining("Cannot parse");
    }
}
