package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.FakeVersionResolver;
import io.github.pgatzka.organizer.support.RecordingLog;
import java.util.Map;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

/** Filling in a missing version from the remote repositories. */
class VersionResolutionTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private AddDependencyMojo mojo(FakeVersionResolver resolver) {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.setLog(log);
        mojo.setVersionResolver(resolver);
        return mojo;
    }

    @Test
    void fillsInTheNewestReleaseVersion() throws Exception {
        AddDependencyMojo mojo = mojo(new FakeVersionResolver(Map.of("org.apache.commons:commons-lang3", "3.17.0")));
        mojo.artifact = "org.apache.commons:commons-lang3";

        mojo.execute();

        assertThat(content()).contains("<version>3.17.0</version>");
        assertThat(log.text()).contains("Resolved the newest release version");
    }

    @Test
    void anExplicitVersionWins() throws Exception {
        FakeVersionResolver resolver = new FakeVersionResolver(Map.of("g:a", "9.9.9"));
        AddDependencyMojo mojo = mojo(resolver);
        mojo.artifact = "g:a:1.0.0";

        mojo.execute();

        assertThat(content()).contains("<version>1.0.0</version>");
        assertThat(resolver.lookups()).isEmpty();
    }

    @Test
    void aManagedVersionWinsOverTheRepositories() throws Exception {
        pomText("<project>\n"
                + "  <dependencyManagement>\n    <dependencies>\n      <dependency>\n"
                + "        <groupId>g</groupId>\n        <artifactId>a</artifactId>\n"
                + "        <version>1.0.0</version>\n      </dependency>\n    </dependencies>\n"
                + "  </dependencyManagement>\n</project>");
        FakeVersionResolver resolver = new FakeVersionResolver(Map.of("g:a", "9.9.9"));
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.setVersionResolver(resolver);
        mojo.artifact = "g:a";

        mojo.execute();

        assertThat(content()).contains("  <dependencies>\n    <dependency>\n      <groupId>g</groupId>");
        assertThat(content()).doesNotContain("9.9.9");
        assertThat(resolver.lookups()).isEmpty();
    }

    @Test
    void snapshotsAreExcludedByDefault() throws Exception {
        AddDependencyMojo mojo = mojo(new FakeVersionResolver(
                Map.of("g:a", "1.0.0"), Map.of("g:a", "2.0.0-SNAPSHOT"), null));
        mojo.artifact = "g:a";

        mojo.execute();

        assertThat(content()).contains("<version>1.0.0</version>");
    }

    @Test
    void snapshotsAreConsideredWhenAsked() throws Exception {
        AddDependencyMojo mojo = mojo(new FakeVersionResolver(
                Map.of("g:a", "1.0.0"), Map.of("g:a", "2.0.0-SNAPSHOT"), null));
        mojo.artifact = "g:a";
        mojo.allowSnapshots = true;

        mojo.execute();

        assertThat(content()).contains("<version>2.0.0-SNAPSHOT</version>");
        assertThat(log.text()).contains("Resolved the newest version");
    }

    @Test
    void resolutionCanBeTurnedOff() {
        AddDependencyMojo mojo = mojo(new FakeVersionResolver(Map.of("g:a", "1.0.0")));
        mojo.artifact = "g:a";
        mojo.resolveLatest = false;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No version given for g:a");
    }

    @Test
    void anUnknownCoordinateStillFailsWithAdvice() {
        AddDependencyMojo mojo = mojo(new FakeVersionResolver(Map.of()));
        mojo.artifact = "g:unknown";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Pass -Dversion=<version>");
    }

    @Test
    void anUnreachableRepositoryFailsWithAdviceRatherThanAStackTrace() {
        AddDependencyMojo mojo = mojo(FakeVersionResolver.failing("repo.example.com timed out"));
        mojo.artifact = "g:a";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("repo.example.com timed out")
                .hasMessageContaining("Pass -Dversion=<version> to skip the lookup");
    }

    @Test
    void managedEntriesResolveToo() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddManagedDependencyMojo mojo = configure(new AddManagedDependencyMojo());
        mojo.setVersionResolver(new FakeVersionResolver(Map.of("g:a", "4.5.6")));
        mojo.artifact = "g:a";

        mojo.execute();

        assertThat(content()).contains("<version>4.5.6</version>");
    }

    @Test
    void bomImportsResolveToo() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo mojo = configure(new ImportBomMojo());
        mojo.setVersionResolver(new FakeVersionResolver(Map.of("g:bom", "7.0.0")));
        mojo.bom = "g:bom";

        mojo.execute();

        assertThat(content()).contains("<version>7.0.0</version>").contains("<scope>import</scope>");
    }

    @Test
    void withoutAMavenRuntimeThereIsNothingToResolveWith() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "g:a";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No version given for g:a");
    }
}
