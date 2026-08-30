package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jdom2.Element;

/** Renders the dependencies a POM declares, for the read-only {@code list-dependencies} goal. */
public final class DependencyReport {

    /** Shown instead of a version when the entry inherits one. */
    public static final String MANAGED_VERSION = "(managed)";

    private static final String NO_SCOPE = "compile";

    /** How the report is laid out. */
    public enum Format {
        /** One coordinate per line. */
        PLAIN,
        /** Aligned columns with a header. */
        TABLE,
        /** Grouped under a heading per scope. */
        TREE;

        /** Parses a {@code -Dformat} value, case-insensitively. */
        public static Format parse(String text) {
            if (text == null || text.isBlank()) {
                return PLAIN;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown format '" + text + "'. Choose one of plain, table or tree.");
            }
        }
    }

    /** One line of the report. */
    public record Entry(Coordinate coordinate, String scope, boolean managed) {

        /** The version to display: the declared one, or a note that it is inherited. */
        public String displayVersion() {
            return coordinate.getVersion() == null ? MANAGED_VERSION : coordinate.getVersion();
        }

        /** {@code groupId:artifactId}. */
        public String ga() {
            return coordinate.toGA();
        }
    }

    private DependencyReport() {}

    /**
     * Collects the declared dependencies of {@code target}.
     *
     * @param includeManaged also collect {@code <dependencyManagement>} entries, flagged as managed
     */
    public static List<Entry> collect(Element target, boolean includeManaged) {
        List<Entry> entries = new ArrayList<>();
        collectInto(entries, Poms.child(target, "dependencies"), false);
        if (includeManaged) {
            collectInto(entries, Poms.path(target, "dependencyManagement", "dependencies").orElse(null), true);
        }
        return entries;
    }

    private static void collectInto(List<Entry> entries, Element container, boolean managed) {
        for (Element dependency : Poms.children(container, "dependency")) {
            entries.add(new Entry(
                    Dependencies.coordinateOf(dependency),
                    Poms.childText(dependency, "scope", NO_SCOPE),
                    managed));
        }
    }

    /**
     * Narrows a report.
     *
     * @param scope keep only this scope, or {@code null} for all
     * @param filter keep only coordinates matching this pattern, or {@code null} for all
     */
    public static List<Entry> filter(List<Entry> entries, String scope, Coordinate filter) {
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (scope != null && !scope.isBlank() && !scope.equals(entry.scope())) {
                continue;
            }
            if (filter != null
                    && !filter.matchesGA(entry.coordinate().getGroupId(), entry.coordinate().getArtifactId())) {
                continue;
            }
            kept.add(entry);
        }
        return kept;
    }

    /** Renders the report. */
    public static List<String> render(List<Entry> entries, Format format) {
        if (entries.isEmpty()) {
            return List.of("No dependencies match.");
        }
        return switch (format) {
            case PLAIN -> renderPlain(entries);
            case TABLE -> renderTable(entries);
            case TREE -> renderTree(entries);
        };
    }

    private static List<String> renderPlain(List<Entry> entries) {
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries) {
            StringBuilder line = new StringBuilder(entry.ga()).append(':').append(entry.displayVersion());
            line.append(" [").append(entry.scope()).append(']');
            if (entry.managed()) {
                line.append(" (dependencyManagement)");
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> renderTable(List<Entry> entries) {
        int groupWidth = width(entries, entry -> entry.coordinate().getGroupId(), "GROUP ID");
        int artifactWidth = width(entries, entry -> entry.coordinate().getArtifactId(), "ARTIFACT ID");
        int versionWidth = width(entries, Entry::displayVersion, "VERSION");

        List<String> lines = new ArrayList<>();
        lines.add(row("GROUP ID", "ARTIFACT ID", "VERSION", "SCOPE", groupWidth, artifactWidth, versionWidth));
        lines.add(row(
                "-".repeat(groupWidth),
                "-".repeat(artifactWidth),
                "-".repeat(versionWidth),
                "-----",
                groupWidth,
                artifactWidth,
                versionWidth));
        for (Entry entry : entries) {
            lines.add(row(
                    entry.coordinate().getGroupId(),
                    entry.coordinate().getArtifactId(),
                    entry.displayVersion(),
                    entry.managed() ? entry.scope() + " (managed)" : entry.scope(),
                    groupWidth,
                    artifactWidth,
                    versionWidth));
        }
        return lines;
    }

    private static List<String> renderTree(List<Entry> entries) {
        Map<String, List<Entry>> byScope = new LinkedHashMap<>();
        for (Entry entry : entries) {
            byScope.computeIfAbsent(entry.managed() ? "dependencyManagement" : entry.scope(),
                    key -> new ArrayList<>()).add(entry);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> group : byScope.entrySet()) {
            lines.add(group.getKey());
            List<Entry> members = group.getValue();
            for (int i = 0; i < members.size(); i++) {
                Entry entry = members.get(i);
                String branch = i == members.size() - 1 ? "\\- " : "+- ";
                lines.add(branch + entry.ga() + ":" + entry.displayVersion());
            }
        }
        return lines;
    }

    private static String row(
            String group, String artifact, String version, String scope, int gw, int aw, int vw) {
        return String.format("%-" + gw + "s  %-" + aw + "s  %-" + vw + "s  %s", group, artifact, version, scope);
    }

    private static int width(
            List<Entry> entries, java.util.function.Function<Entry, String> field, String header) {
        int width = header.length();
        for (Entry entry : entries) {
            String value = field.apply(entry);
            if (value != null) {
                width = Math.max(width, value.length());
            }
        }
        return width;
    }
}
