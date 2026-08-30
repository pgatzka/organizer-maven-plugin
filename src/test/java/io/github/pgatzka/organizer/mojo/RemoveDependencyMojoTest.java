package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class RemoveDependencyMojoTest extends MojoTest {

    private RemoveDependencyMojo mojo() {
        pom("sample.xml");
        return configure(new RemoveDependencyMojo());
    }

    @Test
    void removesTheDependencyAndItsWhitespace() throws Exception {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3";

        mojo.execute();

        assertThat(content())
                .doesNotContain("commons-lang3")
                .contains("  <dependencies>\n    <!-- test scope below -->\n    <dependency>");
    }

    @Test
    void leavesNeighbouringEntriesAndCommentsIntact() throws Exception {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "org.junit.jupiter:junit-jupiter";

        mojo.execute();

        assertThat(content())
                .doesNotContain("junit-jupiter")
                .contains("      <version>3.14.0</version>\n"
                        + "    </dependency>\n"
                        + "    <!-- test scope below -->\n"
                        + "  </dependencies>");
    }

    @Test
    void ignoresTheVersionWhenMatching() throws Exception {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3:1.2.3";

        mojo.execute();

        assertThat(content()).doesNotContain("commons-lang3");
    }

    @Test
    void removesEveryMatchOfAWildcard() throws Exception {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "*:*";

        mojo.execute();

        assertThat(content()).doesNotContain("<dependency>");
    }

    @Test
    void prunesTheDependenciesSectionWhenItIsLeftEmpty() throws Exception {
        pomText("<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void keepsAnEmptySectionWhenAsked() throws Exception {
        pomText("<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.removeEmptyElements = false;

        mojo.execute();

        assertThat(content()).contains("<dependencies>").doesNotContain("<dependency>");
    }

    @Test
    void distinguishesEntriesByClassifier() throws Exception {
        pomText("<project>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n"
                + "      <classifier>tests</classifier>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.classifier = "tests";

        mojo.execute();

        assertThat(content()).contains("<dependency>").doesNotContain("<classifier>tests</classifier>");
    }

    @Test
    void reportsAMissingDependencyWithoutFailing() throws Exception {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:nothing-here";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void failsOnAMissingDependencyWhenAsked() {
        RemoveDependencyMojo mojo = mojo();
        mojo.artifact = "com.acme:nothing-here";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No dependency matching com.acme:nothing-here");
    }

    @Test
    void handlesAPomWithoutAnyDependencies() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void failsOnAPomWithoutDependenciesWhenAsked() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("declares no dependencies");
    }

    @Test
    void offersTheDeclaredDependenciesToChooseFrom() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("2", "y");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0))
                .contains("org.apache.commons:commons-lang3:3.14.0")
                .contains("org.junit.jupiter:junit-jupiter:5.11.4 (test)");
        assertThat(content()).doesNotContain("junit-jupiter").contains("commons-lang3");
    }

    @Test
    void asksBeforeRemoving() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("n");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo(), prompter);
        mojo.artifact = "org.apache.commons:commons-lang3";
        String before = content();

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Remove 1 dependency entry?");
        assertThat(content()).isEqualTo(before);
    }

    @Test
    void skipsTheConfirmationWhenForced() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter();
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo(), prompter);
        mojo.artifact = "org.apache.commons:commons-lang3";
        mojo.force = true;

        mojo.execute();

        assertThat(prompter.questions()).isEmpty();
        assertThat(content()).doesNotContain("commons-lang3");
    }

    @Test
    void failsInBatchModeWithoutACoordinate() {
        RemoveDependencyMojo mojo = mojo();

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Dartifact=<value>");
    }
}
