package io.github.pgatzka.organizer.core;

import java.util.List;
import java.util.Optional;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/** Looks up the newest version of an artifact in the configured remote repositories. */
public class VersionResolver {

    /** Used when no project is loaded and Maven therefore offers no repository list. */
    public static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository.Builder(
                    "central", "default", "https://repo.maven.apache.org/maven2")
            .build();

    /** The open range that asks a repository for every version it knows. */
    private static final String ALL_VERSIONS = "[0,)";

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public VersionResolver(
            RepositorySystem repositorySystem,
            RepositorySystemSession session,
            List<RemoteRepository> repositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = repositories == null || repositories.isEmpty()
                ? List.of(MAVEN_CENTRAL)
                : List.copyOf(repositories);
    }

    /**
     * The newest version of {@code coordinate}.
     *
     * @param allowSnapshots consider {@code -SNAPSHOT} versions too
     * @return the newest matching version, or empty when the repositories know none
     * @throws VersionResolutionException when the repositories could not be reached
     */
    public Optional<String> latestVersion(Coordinate coordinate, boolean allowSnapshots)
            throws VersionResolutionException {
        VersionRangeRequest request = new VersionRangeRequest(
                new DefaultArtifact(
                        coordinate.getGroupId(),
                        coordinate.getArtifactId(),
                        coordinate.getClassifier(),
                        coordinate.getType() == null ? "jar" : coordinate.getType(),
                        ALL_VERSIONS),
                repositories,
                null);
        VersionRangeResult result;
        try {
            result = repositorySystem.resolveVersionRange(session, request);
        } catch (VersionRangeResolutionException e) {
            throw new VersionResolutionException(
                    "Could not look up a version for " + coordinate.toGA() + ": " + e.getMessage(), e);
        }
        return newest(result.getVersions(), allowSnapshots);
    }

    private static Optional<String> newest(List<Version> versions, boolean allowSnapshots) {
        Version best = null;
        for (Version version : versions) {
            if (!allowSnapshots && isSnapshot(version.toString())) {
                continue;
            }
            if (best == null || version.compareTo(best) > 0) {
                best = version;
            }
        }
        return Optional.ofNullable(best).map(Version::toString);
    }

    /** Whether a version string names a snapshot build. */
    public static boolean isSnapshot(String version) {
        return version.endsWith("-SNAPSHOT") || version.matches(".*-\\d{8}\\.\\d{6}-\\d+$");
    }

    /** Thrown when the remote repositories could not answer. */
    public static class VersionResolutionException extends Exception {

        private static final long serialVersionUID = 1L;

        public VersionResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
