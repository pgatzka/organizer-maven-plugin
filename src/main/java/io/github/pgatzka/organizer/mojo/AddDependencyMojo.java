package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.DependencyOptions;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Adds a dependency to the POM.
 *
 * <pre>
 * mvn organizer:add-dependency -Dartifact=org.junit.jupiter:junit-jupiter:5.11.4 -Dscope=test
 * </pre>
 *
 * <p>The version may be left out when {@code dependencyManagement}, an imported BOM or a parent
 * POM already manages it. An entry that is already present is updated in place rather than
 * duplicated.
 */
@Mojo(name = "add-dependency", requiresProject = false, threadSafe = true)
public class AddDependencyMojo extends AbstractDependencyMojo {

    /** Fail instead of updating when the dependency is already declared. */
    @Parameter(property = "failOnExisting", defaultValue = "false")
    boolean failOnExisting;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        Coordinate coordinate = coordinate();
        DependencyOptions options = options();

        // Look before creating anything: an entry that already exists needs no version resolution,
        // and a goal that fails should not leave an empty <dependencies> behind.
        Element container = Poms.child(target, "dependencies");
        Optional<Element> existing =
                container == null ? Optional.empty() : Dependencies.find(container, coordinate);

        if (existing.isPresent()) {
            if (failOnExisting) {
                throw new MojoFailureException(
                        coordinate.toGA() + " is already declared. Drop -DfailOnExisting to update it instead.");
            }
            if (Dependencies.update(existing.get(), coordinate, options, pom.getIndentUnit())) {
                getLog().info("Updated dependency " + Dependencies.describe(existing.get()));
            } else {
                getLog().info("Dependency " + coordinate.toGA() + " is already declared as requested");
            }
            return;
        }

        coordinate = resolveVersion(pom, target, coordinate);
        Element dependency = Dependencies.build(target, coordinate, options);
        Poms.append(
                Poms.childOrCreate(target, "dependencies", pom.getIndentUnit()),
                dependency,
                pom.getIndentUnit());
        getLog().info("Added dependency " + Dependencies.describe(dependency));
    }
}
