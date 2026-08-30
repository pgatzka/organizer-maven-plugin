package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jdom2.Element;

/** Reading and writing {@code <plugin>} entries. */
public final class Plugins {

    /** Where Maven's own plugins live, and the default when a coordinate names only an artifact. */
    public static final String DEFAULT_GROUP_ID = "org.apache.maven.plugins";

    /** The order the POM schema gives to the children of {@code <plugin>}. */
    public static final List<String> ELEMENT_ORDER = List.of(
            "groupId", "artifactId", "version", "extensions", "executions", "dependencies", "goals",
            "inherited", "configuration");

    private Plugins() {}

    /**
     * Parses a plugin coordinate, defaulting the group to {@code org.apache.maven.plugins} so that
     * {@code maven-surefire-plugin} and {@code maven-surefire-plugin:3.5.2} both work.
     *
     * <p>Two segments are ambiguous: {@code a:b} could be a group and an artifact, or an artifact
     * and a version. It is read as artifact and version only when the first segment has no dot in
     * it and the second starts with a digit, which no real group id and artifact id pair does.
     */
    public static Coordinate parseCoordinate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing plugin. Expected [groupId:]artifactId[:version]");
        }
        String trimmed = text.trim();
        if (!trimmed.contains(":")) {
            return Coordinate.of(DEFAULT_GROUP_ID, trimmed, null);
        }
        String[] parts = trimmed.split(":", -1);
        if (parts.length == 2 && looksLikeArtifactAndVersion(parts[0], parts[1])) {
            return Coordinate.of(DEFAULT_GROUP_ID, parts[0], parts[1]);
        }
        return Coordinate.parse(trimmed);
    }

    private static boolean looksLikeArtifactAndVersion(String first, String second) {
        return !first.contains(".") && !second.isEmpty() && Character.isDigit(second.charAt(0));
    }

    /** The coordinate declared by a {@code <plugin>} element, with the group defaulted. */
    public static Coordinate coordinateOf(Element plugin) {
        return Coordinate.of(
                Poms.childText(plugin, "groupId", DEFAULT_GROUP_ID),
                Poms.childText(plugin, "artifactId", "unknown"),
                Poms.childText(plugin, "version"));
    }

    /** Whether {@code plugin} is the one {@code wanted} refers to. */
    public static boolean matches(Element plugin, Coordinate wanted) {
        return wanted.matchesGA(
                Poms.childText(plugin, "groupId", DEFAULT_GROUP_ID), Poms.childText(plugin, "artifactId"));
    }

    /** Every plugin in {@code container} matching {@code wanted}. */
    public static List<Element> findAll(Element container, Coordinate wanted) {
        List<Element> found = new ArrayList<>();
        for (Element plugin : Poms.children(container, "plugin")) {
            if (matches(plugin, wanted)) {
                found.add(plugin);
            }
        }
        return found;
    }

    /** The first plugin in {@code container} matching {@code wanted}. */
    public static Optional<Element> find(Element container, Coordinate wanted) {
        return findAll(container, wanted).stream().findFirst();
    }

    /** Builds a detached {@code <plugin>} element. */
    public static Element build(
            Element context, Coordinate coordinate, Map<String, String> configuration, List<Execution> executions) {
        Element plugin = Poms.element(context, "plugin");
        if (!DEFAULT_GROUP_ID.equals(coordinate.getGroupId())) {
            plugin.addContent(Poms.element(context, "groupId", coordinate.getGroupId()));
        }
        plugin.addContent(Poms.element(context, "artifactId", coordinate.getArtifactId()));
        if (coordinate.getVersion() != null) {
            plugin.addContent(Poms.element(context, "version", coordinate.getVersion()));
        }
        if (!executions.isEmpty()) {
            plugin.addContent(buildExecutions(context, executions));
        }
        if (!configuration.isEmpty()) {
            plugin.addContent(buildConfiguration(context, configuration));
        }
        return plugin;
    }

    /** Builds a {@code <configuration>} element from flat key/value pairs. */
    public static Element buildConfiguration(Element context, Map<String, String> configuration) {
        Element element = Poms.element(context, "configuration");
        configuration.forEach((key, value) -> element.addContent(Poms.element(context, key, value)));
        return element;
    }

    private static Element buildExecutions(Element context, List<Execution> executions) {
        Element container = Poms.element(context, "executions");
        for (Execution execution : executions) {
            Element element = Poms.element(context, "execution");
            if (execution.id() != null) {
                element.addContent(Poms.element(context, "id", execution.id()));
            }
            if (execution.phase() != null) {
                element.addContent(Poms.element(context, "phase", execution.phase()));
            }
            if (!execution.goals().isEmpty()) {
                Element goals = Poms.element(context, "goals");
                for (String goal : execution.goals()) {
                    goals.addContent(Poms.element(context, "goal", goal));
                }
                element.addContent(goals);
            }
            container.addContent(element);
        }
        return container;
    }

    /**
     * Merges a request into an existing {@code <plugin>}: sets the version, merges configuration
     * entries one by one so unrelated settings survive, and appends new executions.
     *
     * @return whether anything changed
     */
    public static boolean merge(
            Element plugin,
            Coordinate coordinate,
            Map<String, String> configuration,
            List<Execution> executions,
            String indentUnit) {
        boolean changed = false;

        if (coordinate.getVersion() != null
                && !coordinate.getVersion().equals(Poms.childText(plugin, "version"))) {
            Poms.setChildTextOrdered(plugin, "version", coordinate.getVersion(), ELEMENT_ORDER, indentUnit);
            changed = true;
        }

        if (!configuration.isEmpty()) {
            Element existing = Poms.child(plugin, "configuration");
            if (existing == null) {
                Poms.setChildOrdered(
                        plugin, buildConfiguration(plugin, configuration), ELEMENT_ORDER, indentUnit);
                changed = true;
            } else {
                for (Map.Entry<String, String> setting : configuration.entrySet()) {
                    if (!setting.getValue().equals(Poms.childText(existing, setting.getKey()))) {
                        Poms.setChildText(existing, setting.getKey(), setting.getValue(), indentUnit);
                        changed = true;
                    }
                }
            }
        }

        if (!executions.isEmpty()) {
            Element container = Poms.childOrCreate(plugin, "executions", indentUnit);
            for (Execution execution : executions) {
                if (findExecution(container, execution.id()).isEmpty()) {
                    Element built = buildExecutions(plugin, List.of(execution));
                    Poms.append(container, built.getChildren().get(0).detach(), indentUnit);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static Optional<Element> findExecution(Element container, String id) {
        for (Element execution : Poms.children(container, "execution")) {
            if (id == null ? Poms.childText(execution, "id") == null : id.equals(Poms.childText(execution, "id"))) {
                return Optional.of(execution);
            }
        }
        return Optional.empty();
    }

    /** A one-line description used in log output and selection prompts. */
    public static String describe(Element plugin) {
        return coordinateOf(plugin).toString();
    }

    /**
     * Parses {@code key=value,key=value} into configuration settings, keeping the order given.
     */
    public static Map<String, String> parseConfiguration(String text) {
        Map<String, String> settings = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return settings;
        }
        for (String pair : text.split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException(
                        "Cannot parse configuration entry '" + pair.trim() + "'. Expected key=value.");
            }
            settings.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return settings;
    }

    /** One {@code <execution>} of a plugin. */
    public record Execution(String id, String phase, List<String> goals) {

        public Execution {
            goals = goals == null ? List.of() : List.copyOf(goals);
        }

        /**
         * Parses {@code id:phase:goal1+goal2}. The phase and goals may be left off, and a leading
         * empty id means "no id".
         */
        public static Execution parse(String text) {
            String[] parts = text.trim().split(":", -1);
            if (parts.length > 3) {
                throw new IllegalArgumentException(
                        "Cannot parse execution '" + text + "'. Expected id[:phase[:goal1+goal2]].");
            }
            String id = parts[0].isBlank() ? null : parts[0].trim();
            String phase = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
            List<String> goals = new ArrayList<>();
            if (parts.length > 2) {
                for (String goal : parts[2].split("\\+")) {
                    if (!goal.isBlank()) {
                        goals.add(goal.trim());
                    }
                }
            }
            if (id == null && phase == null && goals.isEmpty()) {
                throw new IllegalArgumentException("Cannot parse execution '" + text + "': it is empty.");
            }
            return new Execution(id, phase, goals);
        }

        /** Parses a comma-separated list of executions. */
        public static List<Execution> parseAll(String text) {
            List<Execution> executions = new ArrayList<>();
            if (text == null || text.isBlank()) {
                return executions;
            }
            for (String entry : text.split(",")) {
                if (!entry.isBlank()) {
                    executions.add(parse(entry));
                }
            }
            return executions;
        }
    }
}
