package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.FakeVersionResolver;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import java.util.Map;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Running the goals with nothing but the goal name. */
class InteractiveModeTest extends MojoTest {

    private static MavenSession session(boolean interactive) {
        MavenExecutionRequest request = Mockito.mock(MavenExecutionRequest.class);
        Mockito.when(request.isInteractiveMode()).thenReturn(interactive);
        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getRequest()).thenReturn(request);
        return session;
    }

    // ---------------------------------------------------------------- the guided flow

    @Test
    void walksThroughAddingADependency() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "2.0.0", "5");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions())
                .containsExactly("groupId", "artifactId", "Version for com.acme:widget (blank to leave it managed)",
                        "Scope [(none, the default compile scope), compile, provided, runtime, test, system]");
        assertThat(content())
                .contains("      <version>2.0.0</version>\n      <scope>test</scope>");
        assertThat(prompter.isExhausted()).isTrue();
    }

    @Test
    void offersTheResolvedVersionAsTheDefault() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "", "1");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);
        mojo.setVersionResolver(new FakeVersionResolver(Map.of("com.acme:widget", "3.3.3")));

        mojo.execute();

        assertThat(prompter.questions()).contains("Version for com.acme:widget");
        assertThat(content()).contains("<version>3.3.3</version>");
    }

    @Test
    void offersTheManagedVersionAsTheDefault() throws Exception {
        pomText("<project>\n"
                + "  <dependencyManagement>\n    <dependencies>\n      <dependency>\n"
                + "        <groupId>com.acme</groupId>\n        <artifactId>widget</artifactId>\n"
                + "        <version>9.9.9</version>\n      </dependency>\n    </dependencies>\n"
                + "  </dependencyManagement>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "", "1");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);

        mojo.execute();

        assertThat(content()).contains("  <dependencies>\n    <dependency>\n      <groupId>com.acme</groupId>");
    }

    @Test
    void anEmptyVersionLeavesTheDependencyManaged() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "", "1");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);
        mojo.resolveLatest = false;

        mojo.execute();

        assertThat(content()).doesNotContain("<version>");
    }

    @Test
    void choosingTheFirstScopeWritesNone() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "1.0.0", "1");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);

        mojo.execute();

        assertThat(content()).doesNotContain("<scope>");
    }

    @Test
    void doesNotAskWhenTheCoordinateWasPassed() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter();
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(prompter.questions()).isEmpty();
    }

    @Test
    void doesNotAskForAScopeThatWasPassed() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "1.0.0");
        AddDependencyMojo mojo = configure(new AddDependencyMojo(), prompter);
        mojo.scope = "provided";

        mojo.execute();

        assertThat(prompter.questions()).doesNotContain("Scope");
        assertThat(content()).contains("<scope>provided</scope>");
    }

    // ---------------------------------------------------------------- batch mode

    @Test
    void batchModeNeverPrompts() {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("run without -B");
    }

    @Test
    void anInteractiveSessionPrompts() throws Exception {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(true);
        ScriptedPrompter prompter = new ScriptedPrompter("com.acme", "widget", "1.0.0", "1");
        mojo.setPrompter(prompter);

        mojo.execute();

        assertThat(content()).contains("widget");
    }

    @Test
    void theInteractiveFlagOverridesTheSession() {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(true);
        mojo.interactive = false;

        assertThat(mojo.isInteractive()).isFalse();
    }

    @Test
    void theInteractiveFlagCanTurnPromptingOn() {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(false);
        mojo.interactive = true;

        assertThat(mojo.isInteractive()).isTrue();
    }

    @Test
    void withoutASessionNothingPrompts() {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();

        assertThat(mojo.isInteractive()).isFalse();
    }

    @Test
    void aConsolePrompterIsBuiltWhenNoneWasInjected() {
        pom("sample.xml");
        AddDependencyMojo mojo = new AddDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(false);

        assertThat(mojo.prompter()).isNotNull();
        assertThat(mojo.prompter().isInteractive()).isFalse();
    }

    // ---------------------------------------------------------------- destructive confirmations

    @Test
    void batchModeRemovesWithoutAsking() throws Exception {
        pom("sample.xml");
        RemoveDependencyMojo mojo = new RemoveDependencyMojo();
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.session = session(false);
        mojo.artifact = "org.apache.commons:commons-lang3";

        mojo.execute();

        assertThat(content()).doesNotContain("commons-lang3");
    }

    @Test
    void aDryRunNeedsNoConfirmation() throws Exception {
        pom("sample.xml");
        ScriptedPrompter prompter = new ScriptedPrompter();
        RemoveDependencyMojo mojo = configure(new RemoveDependencyMojo(), prompter);
        mojo.artifact = "org.apache.commons:commons-lang3";
        mojo.dryRun = true;
        String before = content();

        mojo.execute();

        assertThat(prompter.questions()).isEmpty();
        assertThat(content()).isEqualTo(before);
    }
}
