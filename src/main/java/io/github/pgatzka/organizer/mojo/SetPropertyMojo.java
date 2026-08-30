package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Sets a build property, creating the {@code <properties>} block when the POM has none.
 *
 * <pre>
 * mvn organizer:set-property -Dproperty=maven.compiler.release -Dvalue=21
 * </pre>
 *
 * <p>Updating an existing property rewrites only its value, so a comment on the line above it
 * stays where it is.
 */
@Mojo(name = "set-property", requiresProject = false, threadSafe = true)
public class SetPropertyMojo extends AbstractPomWriteMojo {

    /** The property name, e.g. {@code maven.compiler.release}. */
    @Parameter(property = "property")
    String property;

    /** The value to set. */
    @Parameter(property = "value")
    String value;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        String name = require(property, "property", "Property name", null);
        String newValue = require(value, "value", "Value for " + name, currentValue(pom, name));

        Element properties = Poms.childOrCreate(targetElement(pom), "properties", pom.getIndentUnit());
        String previous = Poms.childText(properties, name);
        Poms.setChildText(properties, name, newValue, pom.getIndentUnit());

        if (previous == null) {
            getLog().info("Set " + name + " to " + newValue);
        } else if (!previous.equals(newValue)) {
            getLog().info("Changed " + name + " from " + previous + " to " + newValue);
        }
    }

    /** The property's current value, offered as the default when prompting. */
    private String currentValue(PomDocument pom, String name) {
        return Poms.path(pom.getRoot(), "properties")
                .map(properties -> Poms.childText(properties, name))
                .orElse(null);
    }
}
