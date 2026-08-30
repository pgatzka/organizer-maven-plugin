package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.FakeVersionResolver;
import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import java.util.Map;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class PluginMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    // ---------------------------------------------------------------- add

    @Test
    void addsAPluginToAnExistingBuildSection() throws Exception {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";

        mojo.execute();

        assertThat(content())
                .contains("      </plugin>\n"
                        + "      <plugin>\n"
                        + "        <artifactId>maven-jar-plugin</artifactId>\n"
                        + "        <version>3.4.2</version>\n"
                        + "      </plugin>\n"
                        + "    </plugins>");
    }

    @Test
    void createsTheBuildSectionWhenMissing() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <build>\n"
                        + "    <plugins>\n"
                        + "      <plugin>\n"
                        + "        <artifactId>maven-jar-plugin</artifactId>\n"
                        + "        <version>3.4.2</version>\n"
                        + "      </plugin>\n"
                        + "    </plugins>\n"
                        + "  </build>\n"
                        + "</project>");
    }

    @Test
    void writesANonDefaultGroup() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "org.jacoco:jacoco-maven-plugin:0.8.12";

        mojo.execute();

        assertThat(content()).contains("<groupId>org.jacoco</groupId>");
    }

    @Test
    void writesConfiguration() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-surefire-plugin:3.5.2";
        mojo.configuration = "skipTests=true,argLine=-Xmx1g";

        mojo.execute();

        assertThat(content())
                .contains("        <configuration>\n"
                        + "          <skipTests>true</skipTests>\n"
                        + "          <argLine>-Xmx1g</argLine>\n"
                        + "        </configuration>");
    }

    @Test
    void writesExecutions() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";
        mojo.executions = "make-test-jar:package:test-jar";

        mojo.execute();

        assertThat(content())
                .contains("        <executions>\n"
                        + "          <execution>\n"
                        + "            <id>make-test-jar</id>\n"
                        + "            <phase>package</phase>\n"
                        + "            <goals>\n"
                        + "              <goal>test-jar</goal>\n"
                        + "            </goals>\n"
                        + "          </execution>\n"
                        + "        </executions>");
    }

    @Test
    void writesIntoPluginManagementWhenAsked() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";
        mojo.pluginManagement = true;

        mojo.execute();

        assertThat(content())
                .contains("  <build>\n"
                        + "    <pluginManagement>\n"
                        + "      <plugins>\n"
                        + "        <plugin>\n"
                        + "          <artifactId>maven-jar-plugin</artifactId>");
    }

    @Test
    void updatesTheVersionOfAnExistingPlugin() throws Exception {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setLog(log);
        mojo.plugin = "maven-surefire-plugin:3.5.3";

        mojo.execute();

        assertThat(content()).contains("<version>3.5.3</version>").doesNotContain("3.5.2");
        assertThat(content().split("maven-surefire-plugin", -1)).hasSize(2);
        assertThat(log.text()).contains("Updated plugin");
    }

    @Test
    void keepsExistingConfigurationWhenMerging() throws Exception {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-surefire-plugin";
        mojo.configuration = "skipTests=false";

        mojo.execute();

        assertThat(content())
                .contains("          <argLine>-Xmx1g</argLine>\n"
                        + "          <skipTests>false</skipTests>\n"
                        + "        </configuration>");
    }

    @Test
    void overwritesAConfigurationSettingItWasGiven() throws Exception {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-surefire-plugin";
        mojo.configuration = "argLine=-Xmx2g";

        mojo.execute();

        assertThat(content()).contains("<argLine>-Xmx2g</argLine>").doesNotContain("-Xmx1g");
    }

    @Test
    void doesNotAddADuplicateExecution() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo first = configure(new AddPluginMojo());
        first.plugin = "maven-jar-plugin:3.4.2";
        first.executions = "make-test-jar:package:test-jar";
        first.execute();
        String afterFirst = content();

        AddPluginMojo second = configure(new AddPluginMojo());
        second.plugin = "maven-jar-plugin:3.4.2";
        second.executions = "make-test-jar:package:test-jar";
        second.execute();

        assertThat(content()).isEqualTo(afterFirst);
    }

    @Test
    void reportsNoChangeWhenThePluginIsAlreadyAsRequested() throws Exception {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setLog(log);
        mojo.plugin = "maven-surefire-plugin:3.5.2";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("already declared as requested");
    }

    @Test
    void resolvesAMissingPluginVersion() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setVersionResolver(
                new FakeVersionResolver(Map.of("org.apache.maven.plugins:maven-jar-plugin", "3.4.2")));
        mojo.plugin = "maven-jar-plugin";

        mojo.execute();

        assertThat(content()).contains("<version>3.4.2</version>");
    }

    @Test
    void writesNoVersionWhenNoneCanBeResolved() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin";

        mojo.execute();

        assertThat(content()).contains("<artifactId>maven-jar-plugin</artifactId>").doesNotContain("<version>");
    }

    @Test
    void asksForThePluginWhenNotGiven() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("maven-jar-plugin:3.4.2");
        AddPluginMojo mojo = configure(new AddPluginMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Plugin ([groupId:]artifactId[:version])");
        assertThat(content()).contains("maven-jar-plugin");
    }

    @Test
    void rejectsAMalformedConfiguration() {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";
        mojo.configuration = "nonsense";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Expected key=value");
    }

    @Test
    void rejectsAMalformedExecution() {
        pom("sample.xml");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.plugin = "maven-jar-plugin:3.4.2";
        mojo.executions = "a:b:c:d";

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removesAPluginAndPrunesTheBuildSection() throws Exception {
        pom("sample.xml");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.plugin = "maven-surefire-plugin";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).doesNotContain("maven-surefire-plugin").doesNotContain("<build>");
    }

    @Test
    void keepsTheBuildSectionWhenAsked() throws Exception {
        pom("sample.xml");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.plugin = "maven-surefire-plugin";
        mojo.force = true;
        mojo.removeEmptyElements = false;

        mojo.execute();

        assertThat(content()).contains("<build>").doesNotContain("maven-surefire-plugin");
    }

    @Test
    void removesFromPluginManagementWhenAsked() throws Exception {
        pomText("<project>\n"
                + "  <build>\n    <pluginManagement>\n      <plugins>\n        <plugin>\n"
                + "          <artifactId>maven-jar-plugin</artifactId>\n        </plugin>\n"
                + "      </plugins>\n    </pluginManagement>\n"
                + "    <plugins>\n      <plugin>\n        <artifactId>maven-jar-plugin</artifactId>\n"
                + "      </plugin>\n    </plugins>\n  </build>\n</project>");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.plugin = "maven-jar-plugin";
        mojo.pluginManagement = true;
        mojo.force = true;

        mojo.execute();

        assertThat(content()).doesNotContain("pluginManagement").contains("    <plugins>\n      <plugin>");
    }

    @Test
    void reportsAMissingPluginWithoutFailing() throws Exception {
        pom("sample.xml");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.setLog(log);
        mojo.plugin = "maven-shade-plugin";
        mojo.force = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No plugin matching");
    }

    @Test
    void failsOnAMissingPluginWhenAsked() {
        pom("sample.xml");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.plugin = "maven-shade-plugin";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void handlesAPomWithoutABuildSection() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemovePluginMojo mojo = configure(new RemovePluginMojo());
        mojo.setLog(log);
        mojo.plugin = "maven-jar-plugin";

        mojo.execute();

        assertThat(log.text()).contains("declares no build plugins");
    }

    @Test
    void offersThePluginsToChooseFrom() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("1", "y");
        RemovePluginMojo mojo = configure(new RemovePluginMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0))
                .contains("Which plugin should be removed?")
                .contains("org.apache.maven.plugins:maven-surefire-plugin:3.5.2");
        assertThat(content()).doesNotContain("maven-surefire-plugin");
    }

    @Test
    void asksBeforeRemoving() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter("n");
        RemovePluginMojo mojo = configure(new RemovePluginMojo(), prompter);
        mojo.plugin = "maven-surefire-plugin";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }
}
