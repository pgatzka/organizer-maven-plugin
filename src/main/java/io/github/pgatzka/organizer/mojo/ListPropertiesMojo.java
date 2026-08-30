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
 * Prints the properties the POM declares. Read-only.
 *
 * <pre>
 * mvn organizer:list-properties
 * mvn organizer:list-properties -Dfilter=maven.*
 * </pre>
 */
@Mojo(name = "list-properties", requiresProject = false, threadSafe = true)
public class ListPropertiesMojo extends AbstractPomMojo {

    /** Show only properties whose name matches this pattern, where {@code *} is a wildcard. */
    @Parameter(property = "filter")
    String filter;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element properties = Poms.child(targetElement(pom), "properties");
        List<Element> declared = properties == null ? List.of() : properties.getChildren();

        int width = declared.stream()
                .filter(this::matches)
                .mapToInt(entry -> entry.getName().length())
                .max()
                .orElse(0);

        boolean any = false;
        for (Element entry : declared) {
            if (matches(entry)) {
                getLog().info(String.format("%-" + width + "s  %s", entry.getName(), entry.getTextTrim()));
                any = true;
            }
        }
        if (!any) {
            getLog().info("No properties match.");
        }
    }

    private boolean matches(Element entry) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String regex = filter.trim().replace(".", "\\.").replace("*", ".*");
        return entry.getName().matches(regex);
    }
}
