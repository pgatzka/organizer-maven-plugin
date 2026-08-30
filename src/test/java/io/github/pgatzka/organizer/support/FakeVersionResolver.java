package io.github.pgatzka.organizer.support;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.VersionResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A {@link VersionResolver} that answers from a fixed map instead of the network. */
public final class FakeVersionResolver extends VersionResolver {

    private final Map<String, String> releases;
    private final Map<String, String> snapshots;
    private final List<String> lookups = new ArrayList<>();
    private final VersionResolutionException failure;

    public FakeVersionResolver(Map<String, String> releases) {
        this(releases, Map.of(), null);
    }

    public FakeVersionResolver(
            Map<String, String> releases, Map<String, String> snapshots, VersionResolutionException failure) {
        super(null, null, null);
        this.releases = Map.copyOf(releases);
        this.snapshots = Map.copyOf(snapshots);
        this.failure = failure;
    }

    /** A resolver that always fails, as when the repositories are unreachable. */
    public static FakeVersionResolver failing(String message) {
        return new FakeVersionResolver(
                Map.of(), Map.of(), new VersionResolutionException(message, new IllegalStateException(message)));
    }

    /** The coordinates it was asked about, in order. */
    public List<String> lookups() {
        return List.copyOf(lookups);
    }

    @Override
    public Optional<String> latestVersion(Coordinate coordinate, boolean allowSnapshots)
            throws VersionResolutionException {
        lookups.add(coordinate.toGA());
        if (failure != null) {
            throw failure;
        }
        if (allowSnapshots && snapshots.containsKey(coordinate.toGA())) {
            return Optional.of(snapshots.get(coordinate.toGA()));
        }
        return Optional.ofNullable(releases.get(coordinate.toGA()));
    }
}
