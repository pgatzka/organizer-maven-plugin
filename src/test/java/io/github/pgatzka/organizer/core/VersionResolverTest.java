package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VersionResolverTest {

    private final RepositorySystem repositorySystem = mock(RepositorySystem.class);
    private final RepositorySystemSession session = mock(RepositorySystemSession.class);

    private VersionResolver resolver(List<RemoteRepository> repositories) {
        return new VersionResolver(repositorySystem, session, repositories);
    }

    private void repositoryKnows(String... versions) throws Exception {
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.setVersions(parse(versions));
        when(repositorySystem.resolveVersionRange(any(), any())).thenReturn(result);
    }

    private static List<Version> parse(String... versions) throws InvalidVersionSpecificationException {
        GenericVersionScheme scheme = new GenericVersionScheme();
        List<Version> parsed = new ArrayList<>();
        for (String version : versions) {
            parsed.add(scheme.parseVersion(version));
        }
        return parsed;
    }

    @Test
    void picksTheNewestVersion() throws Exception {
        repositoryKnows("1.0.0", "1.10.0", "1.9.0");

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false)).contains("1.10.0");
    }

    @Test
    void skipsSnapshotsByDefault() throws Exception {
        repositoryKnows("1.0.0", "2.0.0-SNAPSHOT");

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false)).contains("1.0.0");
    }

    @Test
    void considersSnapshotsWhenAsked() throws Exception {
        repositoryKnows("1.0.0", "2.0.0-SNAPSHOT");

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), true)).contains("2.0.0-SNAPSHOT");
    }

    @Test
    void skipsTimestampedSnapshots() throws Exception {
        repositoryKnows("1.0.0", "1.1.0-20240102.030405-7");

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false)).contains("1.0.0");
    }

    @Test
    void returnsEmptyWhenTheRepositoriesKnowNoVersion() throws Exception {
        repositoryKnows();

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false)).isEmpty();
    }

    @Test
    void returnsEmptyWhenOnlySnapshotsExistAndTheyAreExcluded() throws Exception {
        repositoryKnows("1.0.0-SNAPSHOT");

        assertThat(resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false)).isEmpty();
    }

    @Test
    void asksForEveryVersionOfTheCoordinate() throws Exception {
        repositoryKnows("1.0.0");
        ArgumentCaptor<VersionRangeRequest> request = ArgumentCaptor.forClass(VersionRangeRequest.class);

        resolver(List.of()).latestVersion(Coordinate.parse("g:a:ignored:tests:test-jar"), false);

        org.mockito.Mockito.verify(repositorySystem).resolveVersionRange(any(), request.capture());
        assertThat(request.getValue().getArtifact().getGroupId()).isEqualTo("g");
        assertThat(request.getValue().getArtifact().getArtifactId()).isEqualTo("a");
        assertThat(request.getValue().getArtifact().getVersion()).isEqualTo("[0,)");
        assertThat(request.getValue().getArtifact().getClassifier()).isEqualTo("tests");
        assertThat(request.getValue().getArtifact().getExtension()).isEqualTo("test-jar");
    }

    @Test
    void defaultsTheExtensionToJar() throws Exception {
        repositoryKnows("1.0.0");
        ArgumentCaptor<VersionRangeRequest> request = ArgumentCaptor.forClass(VersionRangeRequest.class);

        resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false);

        org.mockito.Mockito.verify(repositorySystem).resolveVersionRange(any(), request.capture());
        assertThat(request.getValue().getArtifact().getExtension()).isEqualTo("jar");
    }

    @Test
    void fallsBackToMavenCentralWithoutARepositoryList() throws Exception {
        repositoryKnows("1.0.0");
        ArgumentCaptor<VersionRangeRequest> request = ArgumentCaptor.forClass(VersionRangeRequest.class);

        resolver(null).latestVersion(Coordinate.parse("g:a"), false);

        org.mockito.Mockito.verify(repositorySystem).resolveVersionRange(any(), request.capture());
        assertThat(request.getValue().getRepositories()).containsExactly(VersionResolver.MAVEN_CENTRAL);
    }

    @Test
    void usesTheRepositoriesItWasGiven() throws Exception {
        repositoryKnows("1.0.0");
        RemoteRepository internal =
                new RemoteRepository.Builder("internal", "default", "https://repo.example.com").build();
        ArgumentCaptor<VersionRangeRequest> request = ArgumentCaptor.forClass(VersionRangeRequest.class);

        resolver(List.of(internal)).latestVersion(Coordinate.parse("g:a"), false);

        org.mockito.Mockito.verify(repositorySystem).resolveVersionRange(any(), request.capture());
        assertThat(request.getValue().getRepositories()).containsExactly(internal);
    }

    @Test
    void wrapsAResolutionFailure() throws Exception {
        when(repositorySystem.resolveVersionRange(any(), any()))
                .thenThrow(new VersionRangeResolutionException(new VersionRangeResult(new VersionRangeRequest())));

        assertThatThrownBy(() -> resolver(List.of()).latestVersion(Coordinate.parse("g:a"), false))
                .isInstanceOf(VersionResolver.VersionResolutionException.class)
                .hasMessageContaining("Could not look up a version for g:a");
    }

    @Test
    void recognisesSnapshotVersions() {
        assertThat(VersionResolver.isSnapshot("1.0-SNAPSHOT")).isTrue();
        assertThat(VersionResolver.isSnapshot("1.0-20240102.030405-1")).isTrue();
        assertThat(VersionResolver.isSnapshot("1.0")).isFalse();
        assertThat(VersionResolver.isSnapshot("1.0-RC1")).isFalse();
    }
}
