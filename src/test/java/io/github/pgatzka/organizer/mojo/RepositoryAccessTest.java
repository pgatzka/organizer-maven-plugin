package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.core.VersionResolver;
import io.github.pgatzka.organizer.support.FakeVersionResolver;
import java.util.List;
import java.util.Map;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** How the goals reach the repositories, and what they inherit from the Maven session. */
class RepositoryAccessTest extends MojoTest {

    private static final RemoteRepository INTERNAL =
            new RemoteRepository.Builder("internal", "default", "https://repo.example.com").build();
    private static final RemoteRepository PLUGINS =
            new RemoteRepository.Builder("plugins", "default", "https://plugins.example.com").build();

    private static MavenSession offlineSession() {
        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.isOffline()).thenReturn(true);
        return session;
    }

    /** A project whose effective model manages a version, as a parent POM or a BOM would. */
    private MavenProject projectManaging(String groupId, String artifactId, String version) {
        Dependency managed = new Dependency();
        managed.setGroupId(groupId);
        managed.setArtifactId(artifactId);
        managed.setVersion(version);
        DependencyManagement management = new DependencyManagement();
        management.addDependency(managed);
        Model model = new Model();
        model.setDependencyManagement(management);
        MavenProject project = new MavenProject(model);
        project.setFile(workspace.resolve("pom.xml").toFile());
        return project;
    }

    // ---------------------------------------------------------------- inherited management

    @Test
    void aVersionManagedByTheEffectiveModelIsLeftOut() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.project = projectManaging("com.acme", "widget", "9.9.9");
        mojo.artifact = "com.acme:widget";

        mojo.execute();

        assertThat(content()).contains("<artifactId>widget</artifactId>").doesNotContain("<version>");
    }

    @Test
    void theEffectiveModelIsIgnoredForAnotherPom() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        MavenProject elsewhere = projectManaging("com.acme", "widget", "9.9.9");
        elsewhere.setFile(workspace.resolve("other/pom.xml").toFile());
        mojo.project = elsewhere;
        mojo.artifact = "com.acme:widget";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No version given");
    }

    @Test
    void aProjectWithoutManagementManagesNothing() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        MavenProject bare = new MavenProject(new Model());
        bare.setFile(workspace.resolve("pom.xml").toFile());
        mojo.project = bare;
        mojo.artifact = "com.acme:widget";

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void aProjectWithoutAFileManagesNothing() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.project = new MavenProject(new Model());
        mojo.artifact = "com.acme:widget";

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    // ---------------------------------------------------------------- offline

    @Test
    void addingADependencyOfflineSaysSo() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.setVersionResolver(new FakeVersionResolver(Map.of("com.acme:widget", "1.0.0")));
        mojo.session = offlineSession();
        mojo.artifact = "com.acme:widget";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("while Maven is offline");
    }

    @Test
    void addingAPluginOfflineSaysSo() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setVersionResolver(new FakeVersionResolver(Map.of()));
        mojo.session = offlineSession();
        mojo.plugin = "maven-jar-plugin";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("while Maven is offline");
    }

    @Test
    void anUnreachableRepositoryWhenAddingAPluginSaysSo() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setVersionResolver(FakeVersionResolver.failing("plugins.example.com timed out"));
        mojo.plugin = "maven-jar-plugin";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("plugins.example.com timed out")
                .hasMessageContaining("-Dplugin=g:a:version");
    }

    @Test
    void pluginResolutionCanBeTurnedOff() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.setVersionResolver(new FakeVersionResolver(Map.of("org.apache.maven.plugins:maven-jar-plugin", "3.4.2")));
        mojo.plugin = "maven-jar-plugin";
        mojo.resolveLatest = false;

        mojo.execute();

        assertThat(content()).doesNotContain("<version>");
    }

    // ---------------------------------------------------------------- repository selection

    @Test
    void dependencyGoalsSearchTheProjectRepositories() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.remoteRepositories = List.of(INTERNAL);

        assertThat(mojo.repositoriesToSearch()).containsExactly(INTERNAL);
    }

    @Test
    void pluginGoalsSearchThePluginRepositories() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.remoteRepositories = List.of(INTERNAL);
        mojo.remotePluginRepositories = List.of(PLUGINS);

        assertThat(mojo.repositoriesToSearch()).containsExactly(PLUGINS);
    }

    @Test
    void pluginGoalsFallBackToTheProjectRepositories() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddPluginMojo mojo = configure(new AddPluginMojo());
        mojo.remoteRepositories = List.of(INTERNAL);
        mojo.remotePluginRepositories = List.of();

        assertThat(mojo.repositoriesToSearch()).containsExactly(INTERNAL);
    }

    @Test
    void aResolverIsBuiltOnceTheMavenRuntimeSuppliesOne() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());

        assertThat(mojo.versionResolver()).isNull();

        mojo.repositorySystem = Mockito.mock(RepositorySystem.class);
        mojo.repositorySession = Mockito.mock(RepositorySystemSession.class);

        VersionResolver resolver = mojo.versionResolver();
        assertThat(resolver).isNotNull();
        assertThat(mojo.versionResolver()).isSameAs(resolver);
    }
}
