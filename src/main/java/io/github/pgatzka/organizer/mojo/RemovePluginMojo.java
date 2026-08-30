package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Plugins;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Removes a build plugin.
 *
 * <pre>
 * mvn organizer:remove-plugin -Dplugin=maven-surefire-plugin
 * </pre>
 *
 * <p>Run without {@code -Dplugin} to pick from the plugins the POM declares.
 */
@Mojo(name = "remove-plugin", requiresProject = false, threadSafe = true)
public class RemovePluginMojo extends AbstractPluginMojo {

    /** Fail when the POM does not declare the plugin. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding elements left empty, up to and including {@code <build>}. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element container = Poms.path(targetElement(pom), sectionPath()).orElse(null);
        if (container == null) {
            reportMissing("This POM declares no " + sectionName());
            return;
        }

        Coordinate coordinate =
                plugin != null && !plugin.isBlank() ? coordinate() : chooseExistingPlugin(container);

        List<Element> matches = Plugins.findAll(container, coordinate);
        if (matches.isEmpty()) {
            reportMissing("No plugin matching " + coordinate.toGA() + " in " + pomFile);
            return;
        }

        if (!confirm("Remove " + matches.size() + " plugin" + (matches.size() == 1 ? "" : "s") + "?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        for (Element match : matches) {
            getLog().info("Removed plugin " + Plugins.describe(match));
            if (removeEmptyElements) {
                Poms.removeAndPrune(match);
            } else {
                Poms.remove(container, match);
            }
        }
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
