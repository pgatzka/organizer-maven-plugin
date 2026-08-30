package io.github.pgatzka.organizer.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Arithmetic on version strings, for the convenience options of {@code set-version}. */
public final class Versions {

    /** The {@code -SNAPSHOT} suffix Maven treats as "not released yet". */
    public static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    /** Leading numeric segments, and whatever qualifier follows them. */
    private static final Pattern NUMERIC = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(.*)$");

    /** Which part of a version {@code -Dbump} should increment. */
    public enum Segment {
        MAJOR,
        MINOR,
        PATCH;

        /** Parses a {@code -Dbump} value, case-insensitively. */
        public static Segment parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                        "Unknown segment '" + text + "'. Choose one of major, minor or patch.");
            }
        }
    }

    private Versions() {}

    /** Whether the version is a snapshot. */
    public static boolean isSnapshot(String version) {
        return version != null && version.endsWith(SNAPSHOT_SUFFIX);
    }

    /** The version without its {@code -SNAPSHOT} suffix. */
    public static String release(String version) {
        require(version);
        return isSnapshot(version)
                ? version.substring(0, version.length() - SNAPSHOT_SUFFIX.length())
                : version;
    }

    /** The version with a {@code -SNAPSHOT} suffix, added only if it does not have one. */
    public static String snapshot(String version) {
        require(version);
        return isSnapshot(version) ? version : version + SNAPSHOT_SUFFIX;
    }

    /**
     * Increments one segment, resetting the less significant ones and keeping any qualifier and the
     * snapshot suffix. {@code 1.2.3-SNAPSHOT} bumped by minor becomes {@code 1.3.0-SNAPSHOT}.
     */
    public static String bump(String version, Segment segment) {
        require(version);
        boolean snapshot = isSnapshot(version);
        String core = release(version);

        Matcher matcher = NUMERIC.matcher(core);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Cannot bump '" + version + "': it does not start with a number.");
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        String qualifier = matcher.group(4);

        switch (segment) {
            case MAJOR -> {
                major++;
                minor = 0;
                patch = 0;
            }
            case MINOR -> {
                minor++;
                patch = 0;
            }
            case PATCH -> patch++;
        }

        String bumped = major + "." + minor + "." + patch + qualifier;
        return snapshot ? snapshot(bumped) : bumped;
    }

    /**
     * The usual next development version: the patch segment incremented and {@code -SNAPSHOT} put
     * back on.
     */
    public static String nextSnapshot(String version) {
        return snapshot(bump(release(version), Segment.PATCH));
    }

    private static void require(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("This POM declares no version to work from.");
        }
    }
}
