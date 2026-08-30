package io.github.pgatzka.organizer.core;

import java.util.Objects;

/**
 * A Maven coordinate as typed on the command line.
 *
 * <p>Accepted forms, mirroring {@code dependency:get}:
 *
 * <pre>
 *   groupId:artifactId
 *   groupId:artifactId:version
 *   groupId:artifactId:version:classifier
 *   groupId:artifactId:version:classifier:type
 * </pre>
 *
 * <p>The version is optional throughout: it may be supplied by {@code dependencyManagement}, by an
 * imported BOM, by a parent POM, or resolved from the remote repositories.
 */
public final class Coordinate {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String type;

    public Coordinate(String groupId, String artifactId, String version, String classifier, String type) {
        this.groupId = require(groupId, "groupId");
        this.artifactId = require(artifactId, "artifactId");
        this.version = blankToNull(version);
        this.classifier = blankToNull(classifier);
        this.type = blankToNull(type);
    }

    public static Coordinate of(String groupId, String artifactId, String version) {
        return new Coordinate(groupId, artifactId, version, null, null);
    }

    /** Parses a colon-separated coordinate. */
    public static Coordinate parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing coordinate. Expected groupId:artifactId[:version[:classifier[:type]]]");
        }
        String[] parts = text.trim().split(":", -1);
        if (parts.length < 2 || parts.length > 5) {
            throw new IllegalArgumentException(
                    "Cannot parse coordinate '" + text
                            + "'. Expected groupId:artifactId[:version[:classifier[:type]]]");
        }
        return new Coordinate(
                parts[0],
                parts[1],
                parts.length > 2 ? parts[2] : null,
                parts.length > 3 ? parts[3] : null,
                parts.length > 4 ? parts[4] : null);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    /** The version, or {@code null} when the caller left it to be managed or resolved. */
    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getType() {
        return type;
    }

    public boolean hasVersion() {
        return version != null;
    }

    /** This coordinate with {@code version} filled in. */
    public Coordinate withVersion(String newVersion) {
        return new Coordinate(groupId, artifactId, newVersion, classifier, type);
    }

    /** This coordinate with the version removed. */
    public Coordinate withoutVersion() {
        return new Coordinate(groupId, artifactId, null, classifier, type);
    }

    /** {@code groupId:artifactId}, the identity used when matching existing entries. */
    public String toGA() {
        return groupId + ":" + artifactId;
    }

    /**
     * Whether {@code groupId} and {@code artifactId} match, treating {@code *} as a wildcard for
     * either segment.
     */
    public boolean matchesGA(String otherGroupId, String otherArtifactId) {
        return glob(groupId, otherGroupId) && glob(artifactId, otherArtifactId);
    }

    private static boolean glob(String pattern, String value) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(value);
        }
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }

    private static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Coordinate is missing a " + what);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(groupId).append(':').append(artifactId);
        if (version != null) {
            text.append(':').append(version);
        }
        if (classifier != null) {
            text.append(':').append(classifier);
        }
        if (type != null) {
            text.append(':').append(type);
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Coordinate that)) {
            return false;
        }
        return groupId.equals(that.groupId)
                && artifactId.equals(that.artifactId)
                && Objects.equals(version, that.version)
                && Objects.equals(classifier, that.classifier)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, type);
    }
}
