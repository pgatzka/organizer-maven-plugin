package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.List;

/** The optional parts of a dependency declaration, as given on the command line. */
public record DependencyOptions(String scope, Boolean optional, List<Coordinate> exclusions) {

    public DependencyOptions {
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    /** No scope, no optional flag, no exclusions. */
    public static DependencyOptions none() {
        return new DependencyOptions(null, null, List.of());
    }

    public static DependencyOptions ofScope(String scope) {
        return new DependencyOptions(scope, null, List.of());
    }

    /** Parses {@code groupId:artifactId,groupId:artifactId} into exclusion coordinates. */
    public static List<Coordinate> parseExclusions(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Coordinate> exclusions = new ArrayList<>();
        for (String entry : text.split(",")) {
            if (!entry.isBlank()) {
                exclusions.add(Coordinate.parse(entry.trim()));
            }
        }
        return exclusions;
    }
}
