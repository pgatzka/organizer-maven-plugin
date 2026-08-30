package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/** Shared behaviour for the goals that remove a {@code <dependency>} from some section. */
abstract class AbstractRemoveDependencyMojo extends AbstractDependencyMojo {

    /** The chain of element names leading to the {@code <dependencies>} the goal reads. */
    abstract String[] sectionPath();

    /** What to call one entry in messages, e.g. {@code managed dependency}. */
    abstract String entryName();

    /** Fail when the POM does not declare the dependency. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding {@code <dependencies>} element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        Element container = Poms.path(target, sectionPath()).orElse(null);
        if (container == null) {
            reportMissing("This POM declares no " + entryName() + " entries");
            return;
        }

        Coordinate coordinate = hasCoordinateParameters()
                ? coordinate()
                : chooseExistingDependency(container, "Which " + entryName() + " should be removed?");

        List<Element> matches = Dependencies.findAll(container, coordinate);
        if (matches.isEmpty()) {
            reportMissing("No " + entryName() + " matching " + coordinate.toGA() + " in " + pomFile);
            return;
        }

        if (!confirm("Remove " + matches.size() + " " + entryName() + (matches.size() == 1 ? "" : "s") + "?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        for (Element match : matches) {
            getLog().info("Removed " + entryName() + " " + Dependencies.describe(match));
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
