package io.github.pgatzka.organizer.core;

import java.util.List;
import java.util.Map;

/**
 * The order the Maven POM reference gives to the children of each element.
 *
 * <p>Used both to place a newly created element sensibly and to reorder a whole POM in the
 * {@code organize} goal.
 */
public final class PomSchema {

    /** The children of {@code <project>}, in the order the POM reference recommends. */
    public static final List<String> PROJECT = List.of(
            "modelVersion",
            "parent",
            "groupId",
            "artifactId",
            "version",
            "packaging",
            "name",
            "description",
            "url",
            "inceptionYear",
            "organization",
            "licenses",
            "developers",
            "contributors",
            "mailingLists",
            "prerequisites",
            "modules",
            "scm",
            "issueManagement",
            "ciManagement",
            "distributionManagement",
            "properties",
            "dependencyManagement",
            "dependencies",
            "repositories",
            "pluginRepositories",
            "build",
            "reporting",
            "profiles");

    /** The children of {@code <build>} and of {@code <profile><build>}. */
    public static final List<String> BUILD = List.of(
            "defaultGoal",
            "directory",
            "finalName",
            "sourceDirectory",
            "scriptSourceDirectory",
            "testSourceDirectory",
            "outputDirectory",
            "testOutputDirectory",
            "extensions",
            "filters",
            "resources",
            "testResources",
            "pluginManagement",
            "plugins");

    /** The children of {@code <profile>}. */
    public static final List<String> PROFILE = List.of(
            "id",
            "activation",
            "modules",
            "distributionManagement",
            "properties",
            "dependencyManagement",
            "dependencies",
            "repositories",
            "pluginRepositories",
            "build",
            "reporting");

    /** The children of {@code <repository>} and {@code <pluginRepository>}. */
    public static final List<String> REPOSITORY =
            List.of("id", "name", "url", "layout", "releases", "snapshots");

    /** The children of {@code <parent>}. */
    public static final List<String> PARENT = List.of("groupId", "artifactId", "version", "relativePath");

    /** The children of {@code <execution>}. */
    public static final List<String> EXECUTION =
            List.of("id", "phase", "goals", "inherited", "configuration");

    /** The element orders, keyed by the name of the element whose children they describe. */
    private static final Map<String, List<String>> BY_ELEMENT = Map.ofEntries(
            Map.entry("project", PROJECT),
            Map.entry("build", BUILD),
            Map.entry("profile", PROFILE),
            Map.entry("parent", PARENT),
            Map.entry("dependency", Dependencies.ELEMENT_ORDER),
            Map.entry("plugin", Plugins.ELEMENT_ORDER),
            Map.entry("execution", EXECUTION),
            Map.entry("repository", REPOSITORY),
            Map.entry("pluginRepository", REPOSITORY),
            Map.entry("exclusion", List.of("groupId", "artifactId")));

    private PomSchema() {}

    /**
     * The recommended order for the children of {@code elementName}, or an empty list when the POM
     * reference does not prescribe one — user-defined content such as {@code <properties>} and
     * {@code <configuration>} keeps the order it was written in.
     */
    public static List<String> orderFor(String elementName) {
        return BY_ELEMENT.getOrDefault(elementName, List.of());
    }
}
