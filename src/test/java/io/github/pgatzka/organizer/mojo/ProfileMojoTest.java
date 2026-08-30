package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class ProfileMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private static final String WITH_CI_PROFILE = "<project>\n"
            + "  <profiles>\n"
            + "    <profile>\n"
            + "      <id>ci</id>\n"
            + "    </profile>\n"
            + "  </profiles>\n"
            + "</project>";

    // ---------------------------------------------------------------- add

    @Test
    void addsAProfile() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <profiles>\n"
                        + "    <profile>\n"
                        + "      <id>ci</id>\n"
                        + "    </profile>\n"
                        + "  </profiles>\n"
                        + "</project>");
    }

    @Test
    void writesActiveByDefault() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.profile = "ci";
        mojo.activeByDefault = true;

        mojo.execute();

        assertThat(content())
                .contains("      <activation>\n"
                        + "        <activeByDefault>true</activeByDefault>\n"
                        + "      </activation>");
    }

    @Test
    void writesAJdkActivation() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.profile = "modern";
        mojo.jdkActivation = "[17,)";

        mojo.execute();

        assertThat(content()).contains("        <jdk>[17,)</jdk>");
    }

    @Test
    void writesAPropertyActivationWithAValue() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.profile = "release";
        mojo.activationProperty = "performRelease=true";

        mojo.execute();

        assertThat(content())
                .contains("        <property>\n"
                        + "          <name>performRelease</name>\n"
                        + "          <value>true</value>\n"
                        + "        </property>");
    }

    @Test
    void writesAPropertyActivationWithoutAValue() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.profile = "release";
        mojo.activationProperty = "performRelease";

        mojo.execute();

        assertThat(content()).contains("<name>performRelease</name>").doesNotContain("<value>");
    }

    @Test
    void addingAnExistingProfileChangesNothing() throws Exception {
        pomText(WITH_CI_PROFILE);
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.setLog(log);
        mojo.profile = "ci";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("already exists");
    }

    @Test
    void updatesTheActivationOfAnExistingProfile() throws Exception {
        pomText(WITH_CI_PROFILE);
        AddProfileMojo mojo = configure(new AddProfileMojo());
        mojo.setLog(log);
        mojo.profile = "ci";
        mojo.activeByDefault = true;

        mojo.execute();

        assertThat(content()).contains("<activeByDefault>true</activeByDefault>");
        assertThat(log.text()).contains("Updated the activation");
    }

    @Test
    void failsInBatchModeWithoutAnId() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddProfileMojo mojo = configure(new AddProfileMojo());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Dprofile=<value>");
    }

    @Test
    void asksForTheProfileId() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("ci");
        AddProfileMojo mojo = configure(new AddProfileMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Profile id");
        assertThat(content()).contains("<id>ci</id>");
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removesAProfileAndItsContents() throws Exception {
        pomText("<project>\n  <profiles>\n    <profile>\n      <id>ci</id>\n"
                + "      <dependencies>\n        <dependency>\n          <groupId>g</groupId>\n"
                + "          <artifactId>a</artifactId>\n        </dependency>\n      </dependencies>\n"
                + "    </profile>\n  </profiles>\n</project>");
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo());
        mojo.profile = "ci";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void leavesOtherProfilesAlone() throws Exception {
        pomText("<project>\n  <profiles>\n"
                + "    <profile>\n      <id>one</id>\n    </profile>\n"
                + "    <profile>\n      <id>two</id>\n    </profile>\n"
                + "  </profiles>\n</project>");
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo());
        mojo.profile = "one";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).contains("<id>two</id>").doesNotContain("<id>one</id>");
    }

    @Test
    void reportsAMissingProfileWithoutFailing() throws Exception {
        pomText(WITH_CI_PROFILE);
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo());
        mojo.setLog(log);
        mojo.profile = "nope";
        mojo.force = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No profile with id nope");
    }

    @Test
    void failsOnAMissingProfileWhenAsked() {
        pomText(WITH_CI_PROFILE);
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo());
        mojo.profile = "nope";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void handlesAPomWithoutProfiles() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo());
        mojo.setLog(log);
        mojo.profile = "ci";

        mojo.execute();

        assertThat(log.text()).contains("declares no profiles");
    }

    @Test
    void offersTheProfilesToChooseFrom() throws Exception {
        pomText(WITH_CI_PROFILE);
        ScriptedPrompter prompter = new ScriptedPrompter("1", "y");
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0)).contains("[ci]");
        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void asksBeforeRemoving() throws Exception {
        pomText(WITH_CI_PROFILE);
        ScriptedPrompter prompter = new ScriptedPrompter("n");
        RemoveProfileMojo mojo = configure(new RemoveProfileMojo(), prompter);
        mojo.profile = "ci";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- list

    @Test
    void describesEachProfile() throws Exception {
        pomText("<project>\n  <profiles>\n"
                + "    <profile>\n      <id>ci</id>\n"
                + "      <activation>\n        <activeByDefault>true</activeByDefault>\n      </activation>\n"
                + "      <dependencies>\n        <dependency>\n          <groupId>g</groupId>\n"
                + "          <artifactId>a</artifactId>\n        </dependency>\n      </dependencies>\n"
                + "    </profile>\n"
                + "    <profile>\n      <id>modern</id>\n"
                + "      <activation>\n        <jdk>[17,)</jdk>\n      </activation>\n"
                + "      <properties>\n        <a>1</a>\n        <b>2</b>\n      </properties>\n"
                + "    </profile>\n"
                + "    <profile>\n      <id>manual</id>\n    </profile>\n"
                + "  </profiles>\n</project>");
        ListProfilesMojo mojo = configure(new ListProfilesMojo());
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.messages())
                .containsExactly(
                        "ci",
                        "  activation: active by default",
                        "  contains:   1 dependency",
                        "modern",
                        "  activation: jdk [17,)",
                        "  contains:   2 properties",
                        "manual",
                        "  activation: -P only",
                        "  contains:   nothing");
    }

    @Test
    void saysSoWhenThereAreNoProfiles() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ListProfilesMojo mojo = configure(new ListProfilesMojo());
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.messages()).containsExactly("This POM declares no profiles.");
    }

    // ---------------------------------------------------------------- targeting

    @Test
    void addsADependencyInsideAProfile() throws Exception {
        pomText(WITH_CI_PROFILE);
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <profiles>\n"
                        + "    <profile>\n"
                        + "      <id>ci</id>\n"
                        + "      <dependencies>\n"
                        + "        <dependency>\n"
                        + "          <groupId>com.acme</groupId>\n"
                        + "          <artifactId>widget</artifactId>\n"
                        + "          <version>1.0.0</version>\n"
                        + "        </dependency>\n"
                        + "      </dependencies>\n"
                        + "    </profile>\n"
                        + "  </profiles>\n"
                        + "</project>");
    }

    @Test
    void setsAPropertyInsideAProfile() throws Exception {
        pomText(WITH_CI_PROFILE);
        SetPropertyMojo mojo = configure(new SetPropertyMojo());
        mojo.property = "skipTests";
        mojo.value = "true";
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content())
                .contains("      <properties>\n"
                        + "        <skipTests>true</skipTests>\n"
                        + "      </properties>");
    }

    @Test
    void addsAPluginInsideAProfile() throws Exception {
        pomText(WITH_CI_PROFILE);
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content())
                .contains("      <build>\n"
                        + "        <plugins>\n"
                        + "          <plugin>\n"
                        + "            <artifactId>maven-jar-plugin</artifactId>");
    }

    @Test
    void addsARepositoryInsideAProfile() throws Exception {
        pomText(WITH_CI_PROFILE);
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "internal";
        mojo.url = "https://repo.example.com";
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content()).contains("      <repositories>\n        <repository>\n          <id>internal</id>");
    }

    @Test
    void leavesTheTopLevelAloneWhenTargetingAProfile() throws Exception {
        pomText("<project>\n"
                + "  <dependencies>\n    <dependency>\n      <groupId>top</groupId>\n"
                + "      <artifactId>level</artifactId>\n    </dependency>\n  </dependencies>\n"
                + "  <profiles>\n    <profile>\n      <id>ci</id>\n    </profile>\n  </profiles>\n"
                + "</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.profile = "ci";

        mojo.execute();

        assertThat(content().indexOf("widget")).isGreaterThan(content().indexOf("<profiles>"));
        assertThat(content().split("<dependencies>", -1)).hasSize(3);
    }

    @Test
    void failsOnAnUnknownProfile() {
        pomText(WITH_CI_PROFILE);
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.profile = "nope";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("-DcreateProfile=true");
    }

    @Test
    void createsTheProfileOnDemand() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";
        mojo.profile = "ci";
        mojo.createProfile = true;

        mojo.execute();

        assertThat(content())
                .contains("  <profiles>\n"
                        + "    <profile>\n"
                        + "      <id>ci</id>\n"
                        + "      <dependencies>\n");
    }

    @Test
    void removesADependencyFromInsideAProfile() throws Exception {
        pomText("<project>\n"
                + "  <dependencies>\n    <dependency>\n      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n    </dependency>\n  </dependencies>\n"
                + "  <profiles>\n    <profile>\n      <id>ci</id>\n"
                + "      <dependencies>\n        <dependency>\n          <groupId>com.acme</groupId>\n"
                + "          <artifactId>widget</artifactId>\n        </dependency>\n      </dependencies>\n"
                + "    </profile>\n  </profiles>\n</project>");
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.profile = "ci";
        mojo.force = true;

        mojo.execute();

        assertThat(content())
                .contains("  <dependencies>\n    <dependency>")
                .contains("      <id>ci</id>\n    </profile>");
    }
}
