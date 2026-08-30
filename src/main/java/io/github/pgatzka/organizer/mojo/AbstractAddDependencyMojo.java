package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.DependencyOptions;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/** Shared behaviour for the goals that add a {@code <dependency>} to some section of the POM. */
abstract class AbstractAddDependencyMojo extends AbstractDependencyMojo {

    /** Fail instead of updating when the dependency is already declared. */
    @Parameter(property = "failOnExisting", defaultValue = "false")
    boolean failOnExisting;

    /** The scopes offered when the goal asks, with "no scope" first since it is the common case. */
    private static final List<String> SCOPES =
            List.of("(none, the default compile scope)", "compile", "provided", "runtime", "test", "system");

    /** Set when the user was asked for a version and deliberately left it blank. */
    private boolean versionLeftManaged;

    /** The chain of element names leading to the {@code <dependencies>} the goal writes into. */
    abstract String[] sectionPath();

    /** What to call the POM section in messages, e.g. {@code dependencyManagement}. */
    abstract String sectionName();

    /** What to call one entry in messages, e.g. {@code managed dependency}. */
    abstract String entryName();

    /**
     * Asks for the version, offering whatever is already managed or the newest release as the
     * default. An empty answer leaves the version out, which is what you want when a BOM manages it.
     */
    private Coordinate askForVersion(PomDocument pom, Element target, Coordinate coordinate)
            throws MojoExecutionException {
        if (coordinate.hasVersion()) {
            return coordinate;
        }
        String suggested = Dependencies.managedVersion(managementContainer(target), coordinate)
                .or(() -> effectiveManagedVersion(coordinate))
                .orElse(null);
        if (suggested == null) {
            suggested = resolveVersionExternally(pom, coordinate).orElse(null);
        }
        String answer = prompter().prompt(
                "Version for " + coordinate.toGA() + (suggested == null ? " (blank to leave it managed)" : ""),
                suggested == null ? "" : suggested);
        if (answer.isBlank()) {
            // Answering nothing is an answer: the version comes from somewhere this POM cannot see,
            // so there is nothing left to resolve or complain about.
            versionLeftManaged = true;
            return coordinate;
        }
        return coordinate.withVersion(answer);
    }

    /** Asks which scope to use, unless one was passed on the command line. */
    private void askForScope() {
        if (scope != null && !scope.isBlank()) {
            return;
        }
        int chosen = prompter().select("Scope", SCOPES, 0);
        if (chosen > 0) {
            scope = SCOPES.get(chosen);
        }
    }

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        Coordinate coordinate = coordinate();
        if (wasPrompted() && prompter().isInteractive()) {
            coordinate = askForVersion(pom, target, coordinate);
            askForScope();
        }
        DependencyOptions options = options();

        // Look before creating anything: an entry that already exists needs no version resolution,
        // and a goal that fails should not leave an empty section behind.
        Element container = Poms.path(target, sectionPath()).orElse(null);
        Optional<Element> existing =
                container == null ? Optional.empty() : Dependencies.find(container, coordinate);

        if (existing.isPresent()) {
            if (failOnExisting) {
                throw new MojoFailureException(coordinate.toGA() + " is already declared in " + sectionName()
                        + ". Drop -DfailOnExisting to update it instead.");
            }
            if (Dependencies.update(existing.get(), coordinate, options, pom.getIndentUnit())) {
                getLog().info("Updated " + entryName() + " " + Dependencies.describe(existing.get()));
            } else {
                getLog().info(coordinate.toGA() + " is already declared in " + sectionName() + " as requested");
            }
            return;
        }

        if (!versionLeftManaged) {
            coordinate = resolveVersion(pom, target, coordinate);
        }
        Element dependency = Dependencies.build(target, coordinate, options);
        Poms.append(
                Poms.pathOrCreate(target, pom.getIndentUnit(), sectionPath()), dependency, pom.getIndentUnit());
        getLog().info("Added " + entryName() + " " + Dependencies.describe(dependency));
    }
}
