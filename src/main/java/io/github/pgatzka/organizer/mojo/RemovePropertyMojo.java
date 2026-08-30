package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Removes a build property.
 *
 * <pre>
 * mvn organizer:remove-property -Dproperty=spring.version
 * </pre>
 *
 * <p>Run without {@code -Dproperty} to pick from the properties the POM declares.
 */
@Mojo(name = "remove-property", requiresProject = false, threadSafe = true)
public class RemovePropertyMojo extends AbstractPomWriteMojo {

    /** The property name. */
    @Parameter(property = "property")
    String property;

    /** Fail when the POM does not declare the property. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding {@code <properties>} element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element properties = Poms.child(targetElement(pom), "properties");
        if (properties == null || !Poms.hasElementChildren(properties)) {
            reportMissing("This POM declares no properties");
            return;
        }

        String name = property != null && !property.isBlank()
                ? property
                : choosePropertyName(properties);

        Element target = Poms.child(properties, name);
        if (target == null) {
            reportMissing("No property named " + name + " in " + pomFile);
            return;
        }

        if (!confirm("Remove property " + name + "?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        if (removeEmptyElements) {
            Poms.removeAndPrune(target);
        } else {
            Poms.remove(properties, target);
        }
        getLog().info("Removed property " + name);
    }

    private String choosePropertyName(Element properties) throws MojoFailureException {
        List<Element> declared = properties.getChildren();
        List<String> labels = declared.stream()
                .map(entry -> entry.getName() + " = " + entry.getTextTrim())
                .toList();
        return declared.get(requireChoice("Which property should be removed?", labels, "property")).getName();
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
