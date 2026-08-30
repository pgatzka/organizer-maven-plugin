package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.DependencyOptions;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jdom2.Element;

/** Shared parameters and coordinate handling for the dependency goals. */
abstract class AbstractDependencyMojo extends AbstractPomWriteMojo {

    /** The dependency, as {@code groupId:artifactId[:version[:classifier[:type]]]}. */
    @Parameter(property = "artifact")
    String artifact;

    /** The group id, when not using {@code -Dartifact}. */
    @Parameter(property = "groupId")
    String groupId;

    /** The artifact id, when not using {@code -Dartifact}. */
    @Parameter(property = "artifactId")
    String artifactId;

    /** The version, when not using {@code -Dartifact}. Optional when the version is managed. */
    @Parameter(property = "version")
    String version;

    /** The dependency scope: {@code compile}, {@code provided}, {@code runtime}, {@code test}, {@code system} or {@code import}. */
    @Parameter(property = "scope")
    String scope;

    /** The packaging type, e.g. {@code pom} or {@code test-jar}. */
    @Parameter(property = "type")
    String type;

    /** The classifier, e.g. {@code tests}. */
    @Parameter(property = "classifier")
    String classifier;

    /** Marks the dependency {@code <optional>}. */
    @Parameter(property = "optional")
    Boolean optional;

    /** Exclusions, as {@code groupId:artifactId,groupId:artifactId}. */
    @Parameter(property = "exclusions")
    String exclusions;

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * The coordinate the goal should act on, taken from {@code -Dartifact} or from the individual
     * parameters, and asked for interactively when neither was given.
     */
    Coordinate coordinate() throws MojoFailureException {
        Coordinate parsed = artifact != null && !artifact.isBlank() ? Coordinate.parse(artifact) : null;

        String group = firstNonBlank(groupId, parsed == null ? null : parsed.getGroupId());
        group = require(group, "groupId", "groupId", null);

        String artifactName = firstNonBlank(artifactId, parsed == null ? null : parsed.getArtifactId());
        artifactName = require(artifactName, "artifactId", "artifactId", null);

        String resolvedVersion = firstNonBlank(version, parsed == null ? null : parsed.getVersion());
        String resolvedClassifier = firstNonBlank(classifier, parsed == null ? null : parsed.getClassifier());
        String resolvedType = firstNonBlank(type, parsed == null ? null : parsed.getType());

        return new Coordinate(group, artifactName, resolvedVersion, resolvedClassifier, resolvedType);
    }

    DependencyOptions options() {
        return new DependencyOptions(scope, optional, DependencyOptions.parseExclusions(exclusions));
    }

    /** The {@code <dependencies>} element inside {@code <dependencyManagement>}, if the POM has one. */
    static Element managementContainer(Element target) {
        return Poms.path(target, "dependencyManagement", "dependencies").orElse(null);
    }

    /**
     * Fills in a missing version from {@code <dependencyManagement>}, an imported BOM or the
     * remote repositories, and reports where it came from.
     */
    Coordinate resolveVersion(PomDocument pom, Element target, Coordinate coordinate)
            throws MojoExecutionException, MojoFailureException {
        if (coordinate.hasVersion()) {
            return coordinate;
        }
        Optional<String> managed = Dependencies.managedVersion(managementContainer(target), coordinate);
        if (managed.isPresent()) {
            getLog().info("Version " + managed.get() + " is managed by dependencyManagement; "
                    + "leaving <version> out of the new entry");
            return coordinate;
        }
        Optional<String> inherited = effectiveManagedVersion(coordinate);
        if (inherited.isPresent()) {
            getLog().info("Version " + inherited.get() + " is managed by a parent POM or an imported BOM; "
                    + "leaving <version> out of the new entry");
            return coordinate;
        }
        Optional<String> resolved = resolveVersionExternally(pom, coordinate);
        if (resolved.isPresent()) {
            return coordinate.withVersion(resolved.get());
        }
        throw new MojoFailureException(
                "No version given for " + coordinate.toGA() + " and none is managed by this POM. "
                        + "Pass -Dversion=<version>, add a managed entry with organizer:add-managed-dependency, "
                        + "or import a BOM with organizer:import-bom.");
    }

    /**
     * Looks the coordinate up in the effective model's {@code dependencyManagement}, which Maven has
     * already flattened across parent POMs and imported BOMs.
     *
     * <p>Only consulted when the goal is editing the POM of the project Maven itself loaded;
     * pointing {@code -Dorganizer.pom} at some other file makes that project's inheritance
     * irrelevant.
     */
    Optional<String> effectiveManagedVersion(Coordinate coordinate) {
        if (project == null || project.getFile() == null || pomFile == null) {
            return Optional.empty();
        }
        if (!project.getFile().getAbsoluteFile().equals(pomFile.getAbsoluteFile())) {
            return Optional.empty();
        }
        if (project.getDependencyManagement() == null) {
            return Optional.empty();
        }
        for (Dependency managed : project.getDependencyManagement().getDependencies()) {
            if (coordinate.matchesGA(managed.getGroupId(), managed.getArtifactId())
                    && managed.getVersion() != null) {
                return Optional.of(managed.getVersion());
            }
        }
        return Optional.empty();
    }

    /**
     * Hook for the goals that can look a version up outside the POM. The base implementation knows
     * nothing beyond the POM itself.
     */
    Optional<String> resolveVersionExternally(PomDocument pom, Coordinate coordinate)
            throws MojoExecutionException {
        return Optional.empty();
    }

    /** Lets the user pick from the dependencies already declared. */
    Coordinate chooseExistingDependency(Element container, String question) throws MojoFailureException {
        List<Element> existing = Poms.children(container, "dependency");
        if (existing.isEmpty()) {
            throw new MojoFailureException("This POM declares no dependencies to choose from.");
        }
        List<String> labels = existing.stream().map(Dependencies::describe).toList();
        return Dependencies.coordinateOf(existing.get(requireChoice(question, labels, "artifact")));
    }

    static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
