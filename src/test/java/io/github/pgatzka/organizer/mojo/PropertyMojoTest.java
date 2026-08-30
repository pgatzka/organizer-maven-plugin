package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class PropertyMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    // ---------------------------------------------------------------- set

    @Test
    void updatesAnExistingPropertyWithAOneLineDiff() throws Exception {
        pom("sample.xml");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.setLog(log);
        mojo.property = "spring.version";
        mojo.value = "6.2.0";

        mojo.execute();

        assertThat(content())
                .contains("    <!-- pinned deliberately, see PLAT-411 -->\n    <spring.version>6.2.0</spring.version>");
        assertThat(log.text()).contains("Changed spring.version from 6.1.0 to 6.2.0");
    }

    @Test
    void addsANewPropertyToAnExistingBlock() throws Exception {
        pom("sample.xml");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.property = "project.build.sourceEncoding";
        mojo.value = "UTF-8";

        mojo.execute();

        assertThat(content())
                .contains("    <spring.version>6.1.0</spring.version>\n"
                        + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                        + "  </properties>");
    }

    @Test
    void createsThePropertiesBlockWhenMissing() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.property = "maven.compiler.release";
        mojo.value = "21";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <properties>\n"
                        + "    <maven.compiler.release>21</maven.compiler.release>\n"
                        + "  </properties>\n"
                        + "</project>");
    }

    @Test
    void settingTheSameValueChangesNothing() throws Exception {
        pom("sample.xml");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.setLog(log);
        mojo.property = "spring.version";
        mojo.value = "6.1.0";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No changes");
    }

    @Test
    void asksForNameAndValueWhenNotGiven() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("new.property", "42");
        SetPropertyMojo mojo = configure(new SetPropertyMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Property name", "Value for new.property");
        assertThat(content()).contains("<new.property>42</new.property>");
    }

    @Test
    void offersTheCurrentValueAsTheDefault() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("");
        SetPropertyMojo mojo = configure(new SetPropertyMojo(), prompter);
        mojo.property = "spring.version";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void failsInBatchModeWithoutAName() {
        pom("sample.xml");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Dproperty=<value>");
    }

    @Test
    void failsInBatchModeWithoutAValue() {
        pom("sample.xml");
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.property = "a.property";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Dvalue=<value>");
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removesAProperty() throws Exception {
        pom("sample.xml");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.property = "spring.version";
        mojo.force = true;

        mojo.execute();

        assertThat(content())
                .doesNotContain("spring.version")
                .contains("    <maven.compiler.release>17</maven.compiler.release>\n"
                        + "    <!-- pinned deliberately, see PLAT-411 -->\n"
                        + "  </properties>");
    }

    @Test
    void prunesAnEmptyPropertiesBlock() throws Exception {
        pomText("<project>\n  <properties>\n    <only.one>x</only.one>\n  </properties>\n</project>");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.property = "only.one";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void keepsAnEmptyBlockWhenAsked() throws Exception {
        pomText("<project>\n  <properties>\n    <only.one>x</only.one>\n  </properties>\n</project>");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.property = "only.one";
        mojo.force = true;
        mojo.removeEmptyElements = false;

        mojo.execute();

        assertThat(content()).contains("<properties>").doesNotContain("only.one");
    }

    @Test
    void reportsAMissingPropertyWithoutFailing() throws Exception {
        pom("sample.xml");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.setLog(log);
        mojo.property = "not.there";
        mojo.force = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No property named not.there");
    }

    @Test
    void failsOnAMissingPropertyWhenAsked() {
        pom("sample.xml");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.property = "not.there";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void handlesAPomWithoutProperties() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo());
        mojo.setLog(log);
        mojo.property = "x";

        mojo.execute();

        assertThat(log.text()).contains("declares no properties");
    }

    @Test
    void offersThePropertiesToChooseFrom() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("2", "y");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0))
                .contains("maven.compiler.release = 17")
                .contains("spring.version = 6.1.0");
        assertThat(content()).doesNotContain("spring.version");
    }

    @Test
    void asksBeforeRemoving() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("n");
        RemovePropertyMojo mojo = configure(new RemovePropertyMojo(), prompter);
        mojo.property = "spring.version";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- list

    @Test
    void listsPropertiesAligned() throws Exception {
        pom("sample.xml");
        ListPropertiesMojo mojo = configure(new ListPropertiesMojo());
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.messages())
                .containsExactly("maven.compiler.release  17", "spring.version          6.1.0");
    }

    @Test
    void filtersPropertiesByName() throws Exception {
        pom("sample.xml");
        ListPropertiesMojo mojo = configure(new ListPropertiesMojo());
        mojo.setLog(log);
        mojo.filter = "maven.*";

        mojo.execute();

        assertThat(log.messages()).containsExactly("maven.compiler.release  17");
    }

    @Test
    void treatsDotsInAFilterAsLiterals() throws Exception {
        pom("sample.xml");
        ListPropertiesMojo mojo = configure(new ListPropertiesMojo());
        mojo.setLog(log);
        mojo.filter = "mavenXcompiler*";

        mojo.execute();

        assertThat(log.messages()).containsExactly("No properties match.");
    }

    @Test
    void saysSoWhenThereAreNoProperties() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ListPropertiesMojo mojo = configure(new ListPropertiesMojo());
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.messages()).containsExactly("No properties match.");
    }

    @Test
    void listingNeverWritesTheFile() throws Exception {
        pom("sample.xml");
        ListPropertiesMojo mojo = configure(new ListPropertiesMojo());
        mojo.setLog(log);
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }
}
