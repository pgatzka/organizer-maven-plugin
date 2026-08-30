package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.Fixtures;
import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class ModuleMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private static final String AGGREGATOR = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "  <modelVersion>4.0.0</modelVersion>\n"
            + "  <groupId>com.example</groupId>\n"
            + "  <artifactId>parent</artifactId>\n"
            + "  <version>1.0.0</version>\n"
            + "  <packaging>pom</packaging>\n"
            + "  <modules>\n"
            + "    <module>alpha</module>\n"
            + "    <module>gamma</module>\n"
            + "  </modules>\n"
            + "</project>";

    // ---------------------------------------------------------------- add

    @Test
    void insertsAModuleInSortedOrder() throws Exception {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "beta";

        mojo.execute();

        assertThat(content())
                .contains("  <modules>\n"
                        + "    <module>alpha</module>\n"
                        + "    <module>beta</module>\n"
                        + "    <module>gamma</module>\n"
                        + "  </modules>");
    }

    @Test
    void appendsToAHandOrderedList() throws Exception {
        pomText("<project>\n  <modules>\n    <module>zeta</module>\n    <module>alpha</module>\n"
                + "  </modules>\n</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "beta";
        mojo.updatePackaging = false;

        mojo.execute();

        assertThat(content())
                .contains("<module>zeta</module>\n    <module>alpha</module>\n    <module>beta</module>");
    }

    @Test
    void createsTheModulesSectionWhenMissing() throws Exception {
        pomText("<project>\n  <artifactId>parent</artifactId>\n</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "first";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>parent</artifactId>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>first</module>\n"
                        + "  </modules>\n"
                        + "</project>");
    }

    @Test
    void switchesPackagingToPom() throws Exception {
        pomText("<project>\n  <artifactId>parent</artifactId>\n  <packaging>jar</packaging>\n</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.setLog(log);
        mojo.module = "first";

        mojo.execute();

        assertThat(content()).contains("<packaging>pom</packaging>").doesNotContain("jar");
        assertThat(log.text()).contains("Changed packaging from jar to pom");
    }

    @Test
    void leavesPackagingAloneWhenAsked() throws Exception {
        pomText("<project>\n  <artifactId>parent</artifactId>\n</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "first";
        mojo.updatePackaging = false;

        mojo.execute();

        assertThat(content()).doesNotContain("packaging");
    }

    @Test
    void leavesAnAlreadyPomPackagingAlone() throws Exception {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "beta";

        mojo.execute();

        assertThat(content().split("<packaging>", -1)).hasSize(2);
    }

    @Test
    void addingAListedModuleChangesNothing() throws Exception {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.setLog(log);
        mojo.module = "alpha";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("already listed");
    }

    @Test
    void scaffoldsAChildPom() throws Exception {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "beta";
        mojo.scaffold = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("beta/pom.xml")))
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n\n"
                        + "  <parent>\n"
                        + "    <groupId>com.example</groupId>\n"
                        + "    <artifactId>parent</artifactId>\n"
                        + "    <version>1.0.0</version>\n"
                        + "  </parent>\n\n"
                        + "  <artifactId>beta</artifactId>\n"
                        + "</project>\n");
    }

    @Test
    void scaffoldingTakesTheGroupFromTheAggregatorsParent() throws Exception {
        pomText("<project>\n"
                + "  <parent>\n    <groupId>com.example</groupId>\n"
                + "    <artifactId>grandparent</artifactId>\n    <version>2.0.0</version>\n  </parent>\n"
                + "  <artifactId>parent</artifactId>\n"
                + "</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "child";
        mojo.scaffold = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("child/pom.xml")))
                .contains("<groupId>com.example</groupId>")
                .contains("<artifactId>parent</artifactId>")
                .contains("<version>2.0.0</version>");
    }

    @Test
    void doesNotOverwriteAnExistingChildPom() throws Exception {
        pomText(AGGREGATOR);
        Fixtures.write(workspace.resolve("beta/pom.xml"), "<project>mine</project>");
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.setLog(log);
        mojo.module = "beta";
        mojo.scaffold = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("beta/pom.xml"))).isEqualTo("<project>mine</project>");
        assertThat(log.text()).contains("Leaving the existing POM");
    }

    @Test
    void doesNotScaffoldWithoutTheFlag() throws Exception {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());
        mojo.module = "beta";

        mojo.execute();

        assertThat(workspace.resolve("beta")).doesNotExist();
    }

    @Test
    void asksForTheModuleName() throws Exception {
        pomText(AGGREGATOR);
        ScriptedPrompter prompter = new ScriptedPrompter("beta");
        AddModuleMojo mojo = configure(new AddModuleMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Module directory");
        assertThat(content()).contains("<module>beta</module>");
    }

    @Test
    void failsInBatchModeWithoutAModule() {
        pomText(AGGREGATOR);
        AddModuleMojo mojo = configure(new AddModuleMojo());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Dmodule=<value>");
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removesAModuleEntry() throws Exception {
        pomText(AGGREGATOR);
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.module = "alpha";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).contains("  <modules>\n    <module>gamma</module>\n  </modules>");
    }

    @Test
    void prunesAnEmptyModulesSection() throws Exception {
        pomText("<project>\n  <modules>\n    <module>only</module>\n  </modules>\n</project>");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.module = "only";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void neverDeletesTheDirectoryByDefault() throws Exception {
        pomText(AGGREGATOR);
        Fixtures.write(workspace.resolve("alpha/pom.xml"), "<project/>");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.module = "alpha";
        mojo.force = true;

        mojo.execute();

        assertThat(workspace.resolve("alpha/pom.xml")).exists();
    }

    @Test
    void deletesTheDirectoryWhenAskedAndConfirmed() throws Exception {
        pomText(AGGREGATOR);
        Fixtures.write(workspace.resolve("alpha/src/main/java/A.java"), "class A {}");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.module = "alpha";
        mojo.deleteDirectory = true;
        mojo.force = true;

        mojo.execute();

        assertThat(workspace.resolve("alpha")).doesNotExist();
    }

    @Test
    void keepsTheDirectoryWhenTheConfirmationIsDeclined() throws Exception {
        pomText(AGGREGATOR);
        Fixtures.write(workspace.resolve("alpha/pom.xml"), "<project/>");
        ScriptedPrompter prompter = new ScriptedPrompter("y", "n");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo(), prompter);
        mojo.module = "alpha";
        mojo.deleteDirectory = true;

        mojo.execute();

        assertThat(content()).doesNotContain("alpha");
        assertThat(workspace.resolve("alpha/pom.xml")).exists();
    }

    @Test
    void aDryRunDeletesNothing() throws Exception {
        pomText(AGGREGATOR);
        Fixtures.write(workspace.resolve("alpha/pom.xml"), "<project/>");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.setLog(log);
        mojo.module = "alpha";
        mojo.deleteDirectory = true;
        mojo.force = true;
        mojo.dryRun = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(workspace.resolve("alpha/pom.xml")).exists();
        assertThat(log.text()).contains("Dry run, not deleting");
    }

    @Test
    void reportsAMissingDirectory() throws Exception {
        pomText(AGGREGATOR);
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.setLog(log);
        mojo.module = "alpha";
        mojo.deleteDirectory = true;
        mojo.force = true;

        mojo.execute();

        assertThat(log.text()).contains("to delete");
    }

    @Test
    void reportsAMissingModuleWithoutFailing() throws Exception {
        pomText(AGGREGATOR);
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.setLog(log);
        mojo.module = "nope";
        mojo.force = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No module named nope");
    }

    @Test
    void failsOnAMissingModuleWhenAsked() {
        pomText(AGGREGATOR);
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.module = "nope";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void handlesAPomWithoutModules() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo());
        mojo.setLog(log);
        mojo.module = "alpha";

        mojo.execute();

        assertThat(log.text()).contains("lists no modules");
    }

    @Test
    void offersTheModulesToChooseFrom() throws Exception {
        pomText(AGGREGATOR);
        ScriptedPrompter prompter = new ScriptedPrompter("2", "y");
        RemoveModuleMojo mojo = configure(new RemoveModuleMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0)).contains("[alpha, gamma]");
        assertThat(content()).doesNotContain("gamma");
    }
}
