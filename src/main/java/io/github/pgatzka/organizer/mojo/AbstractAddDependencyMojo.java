package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.DependencyOptions;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
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

    /** The chain of element names leading to the {@code <dependencies>} the goal writes into. */
    abstract String[] sectionPath();

    /** What to call the POM section in messages, e.g. {@code dependencyManagement}. */
    abstract String sectionName();

    /** What to call one entry in messages, e.g. {@code managed dependency}. */
    abstract String entryName();

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        Coordinate coordinate = coordinate();
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

        coordinate = resolveVersion(pom, target, coordinate);
        Element dependency = Dependencies.build(target, coordinate, options);
        Poms.append(
                Poms.pathOrCreate(target, pom.getIndentUnit(), sectionPath()), dependency, pom.getIndentUnit());
        getLog().info("Added " + entryName() + " " + Dependencies.describe(dependency));
    }
}
