package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.Fixtures;
import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class SetVersionMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private SetVersionMojo mojo() {
        pom("sample.xml");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.setLog(log);
        return mojo;
    }

    @Test
    void setsAnExplicitVersion() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.newVersion = "2.0.0";

        mojo.execute();

        assertThat(content()).contains("<version>2.0.0</version>").doesNotContain("1.2.3-SNAPSHOT");
        assertThat(log.text()).contains("Set version to 2.0.0 (was 1.2.3-SNAPSHOT)");
    }

    @Test
    void changesOnlyTheProjectVersion() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.newVersion = "2.0.0";

        mojo.execute();

        assertThat(content())
                .contains("<version>3.14.0</version>")
                .contains("<version>5.11.4</version>")
                .contains("<version>3.5.2</version>");
        assertThat(log.text()).contains("2 lines changed");
    }

    @Test
    void bumpsASegment() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.bump = "minor";

        mojo.execute();

        assertThat(content()).contains("<version>1.3.0-SNAPSHOT</version>");
    }

    @Test
    void dropsTheSnapshotSuffix() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.releaseVersion = true;

        mojo.execute();

        assertThat(content()).contains("<version>1.2.3</version>");
    }

    @Test
    void movesToTheNextSnapshot() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.nextSnapshot = true;

        mojo.execute();

        assertThat(content()).contains("<version>1.2.4-SNAPSHOT</version>");
    }

    @Test
    void rejectsSeveralOptionsAtOnce() {
        SetVersionMojo mojo = mojo();
        mojo.newVersion = "2.0.0";
        mojo.bump = "minor";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("not several");
    }

    @Test
    void rejectsAnUnknownSegment() {
        SetVersionMojo mojo = mojo();
        mojo.bump = "build";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("major, minor or patch");
    }

    @Test
    void reportsWhenTheVersionIsAlreadyRight() throws Exception {
        SetVersionMojo mojo = mojo();
        mojo.newVersion = "1.2.3-SNAPSHOT";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("Version is already 1.2.3-SNAPSHOT");
    }

    @Test
    void asksForTheVersionWhenNoOptionWasGiven() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("3.0.0");
        SetVersionMojo mojo = configure(new SetVersionMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("New version");
        assertThat(content()).contains("<version>3.0.0</version>");
    }

    @Test
    void addsAVersionToAPomThatOnlyInheritsOne() throws Exception {
        pomText("<project>\n  <parent>\n    <groupId>g</groupId>\n    <artifactId>p</artifactId>\n"
                + "    <version>1.0.0</version>\n  </parent>\n  <artifactId>child</artifactId>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";

        mojo.execute();

        assertThat(content())
                .contains("  <artifactId>child</artifactId>\n  <version>2.0.0</version>")
                .contains("    <version>1.0.0</version>");
    }

    @Test
    void updatesTheParentVersionWhenItMatches() throws Exception {
        pomText("<project>\n  <parent>\n    <groupId>g</groupId>\n    <artifactId>p</artifactId>\n"
                + "    <version>1.0.0</version>\n  </parent>\n  <artifactId>child</artifactId>\n"
                + "  <version>1.0.0</version>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.setLog(log);
        mojo.newVersion = "2.0.0";
        mojo.updateParent = true;

        mojo.execute();

        assertThat(content()).doesNotContain("1.0.0");
        assertThat(log.text()).contains("Updated the parent version to 2.0.0");
    }

    @Test
    void leavesADifferentParentVersionAlone() throws Exception {
        pomText("<project>\n  <parent>\n    <groupId>g</groupId>\n    <artifactId>p</artifactId>\n"
                + "    <version>9.9.9</version>\n  </parent>\n  <artifactId>child</artifactId>\n"
                + "  <version>1.0.0</version>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";
        mojo.updateParent = true;

        mojo.execute();

        assertThat(content()).contains("<version>9.9.9</version>");
    }

    @Test
    void failsWhenThereIsNoVersionAnywhere() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.bump = "patch";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("declares no version and inherits none");
    }

    // ---------------------------------------------------------------- reactor

    private void aggregatorWithChildren() {
        pomText("<project>\n"
                + "  <groupId>com.example</groupId>\n"
                + "  <artifactId>parent</artifactId>\n"
                + "  <version>1.0.0</version>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <modules>\n    <module>alpha</module>\n    <module>beta</module>\n  </modules>\n"
                + "</project>");
        for (String module : new String[] {"alpha", "beta"}) {
            Fixtures.write(
                    workspace.resolve(module + "/pom.xml"),
                    "<project>\n  <parent>\n    <groupId>com.example</groupId>\n"
                            + "    <artifactId>parent</artifactId>\n    <version>1.0.0</version>\n  </parent>\n"
                            + "  <artifactId>" + module + "</artifactId>\n</project>");
        }
    }

    @Test
    void updatesEveryChildsParentVersion() throws Exception {
        aggregatorWithChildren();
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";
        mojo.updateChildren = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("alpha/pom.xml"))).contains("<version>2.0.0</version>");
        assertThat(Fixtures.read(workspace.resolve("beta/pom.xml"))).contains("<version>2.0.0</version>");
    }

    @Test
    void recursesIntoNestedAggregators() throws Exception {
        pomText("<project>\n  <artifactId>root</artifactId>\n  <version>1.0.0</version>\n"
                + "  <modules>\n    <module>mid</module>\n  </modules>\n</project>");
        Fixtures.write(
                workspace.resolve("mid/pom.xml"),
                "<project>\n  <parent>\n    <artifactId>root</artifactId>\n    <version>1.0.0</version>\n"
                        + "  </parent>\n  <artifactId>mid</artifactId>\n"
                        + "  <modules>\n    <module>leaf</module>\n  </modules>\n</project>");
        Fixtures.write(
                workspace.resolve("mid/leaf/pom.xml"),
                "<project>\n  <parent>\n    <artifactId>mid</artifactId>\n    <version>1.0.0</version>\n"
                        + "  </parent>\n  <artifactId>leaf</artifactId>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";
        mojo.updateChildren = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("mid/leaf/pom.xml"))).contains("<version>2.0.0</version>");
    }

    @Test
    void leavesChildrenPointingAtAnotherVersionAlone() throws Exception {
        aggregatorWithChildren();
        Fixtures.write(
                workspace.resolve("alpha/pom.xml"),
                "<project>\n  <parent>\n    <artifactId>other</artifactId>\n    <version>9.9.9</version>\n"
                        + "  </parent>\n  <artifactId>alpha</artifactId>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";
        mojo.updateChildren = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("alpha/pom.xml"))).contains("<version>9.9.9</version>");
    }

    @Test
    void warnsAboutAModuleWithoutAPom() throws Exception {
        pomText("<project>\n  <artifactId>root</artifactId>\n  <version>1.0.0</version>\n"
                + "  <modules>\n    <module>missing</module>\n  </modules>\n</project>");
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.setLog(log);
        mojo.newVersion = "2.0.0";
        mojo.updateChildren = true;

        mojo.execute();

        assertThat(log.text()).contains("Module missing has no POM");
    }

    @Test
    void aDryRunWritesNoChildEither() throws Exception {
        aggregatorWithChildren();
        String childBefore = Fixtures.read(workspace.resolve("alpha/pom.xml"));
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";
        mojo.updateChildren = true;
        mojo.dryRun = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("alpha/pom.xml"))).isEqualTo(childBefore);
        assertThat(content()).contains("<version>1.0.0</version>");
    }

    @Test
    void doesNotTouchChildrenWithoutTheFlag() throws Exception {
        aggregatorWithChildren();
        String childBefore = Fixtures.read(workspace.resolve("alpha/pom.xml"));
        SetVersionMojo mojo = configure(new SetVersionMojo());
        mojo.newVersion = "2.0.0";

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("alpha/pom.xml"))).isEqualTo(childBefore);
    }
}
