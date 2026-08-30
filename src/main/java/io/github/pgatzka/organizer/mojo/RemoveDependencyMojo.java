package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Removes a dependency from the POM.
 *
 * <pre>
 * mvn organizer:remove-dependency -Dartifact=org.junit.jupiter:junit-jupiter
 * </pre>
 *
 * <p>Run without {@code -Dartifact} to pick from the dependencies the POM declares. The version is
 * ignored when matching, and {@code *} works as a wildcard in either coordinate segment.
 */
@Mojo(name = "remove-dependency", requiresProject = false, threadSafe = true)
public class RemoveDependencyMojo extends AbstractDependencyMojo {

    /** Fail when the POM does not declare the dependency. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding {@code <dependencies>} element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        Element container = Poms.child(target, "dependencies");
        if (container == null) {
            reportMissing("This POM declares no dependencies");
            return;
        }

        Coordinate coordinate = hasCoordinateParameters()
                ? coordinate()
                : chooseExistingDependency(container, "Which dependency should be removed?");

        List<Element> matches = Dependencies.findAll(container, coordinate);
        if (matches.isEmpty()) {
            reportMissing("No dependency matching " + coordinate.toGA() + " in " + pomFile);
            return;
        }

        if (!confirm("Remove " + matches.size() + " dependency entr" + (matches.size() == 1 ? "y" : "ies") + "?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        for (Element match : matches) {
            getLog().info("Removed dependency " + Dependencies.describe(match));
            if (removeEmptyElements) {
                Poms.removeAndPrune(match);
            } else {
                Poms.remove(container, match);
            }
        }
    }

    private boolean hasCoordinateParameters() {
        return firstNonBlank(artifact, firstNonBlank(groupId, artifactId)) != null;
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
