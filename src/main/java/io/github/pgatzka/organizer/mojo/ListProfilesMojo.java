package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.jdom2.Element;

/**
 * Prints the profiles the POM declares, with how each one is activated and what it contains.
 * Read-only.
 *
 * <pre>
 * mvn organizer:list-profiles
 * </pre>
 */
@Mojo(name = "list-profiles", requiresProject = false, threadSafe = true)
public class ListProfilesMojo extends AbstractPomMojo {

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        List<Element> profiles = Poms.children(Poms.child(pom.getRoot(), "profiles"), "profile");
        if (profiles.isEmpty()) {
            getLog().info("This POM declares no profiles.");
            return;
        }
        for (Element declared : profiles) {
            getLog().info(Poms.childText(declared, "id", "(no id)"));
            getLog().info("  activation: " + describeActivation(declared));
            getLog().info("  contains:   " + describeContents(declared));
        }
    }

    private String describeActivation(Element profile) {
        Element activation = Poms.child(profile, "activation");
        if (activation == null) {
            return "-P only";
        }
        List<String> parts = new ArrayList<>();
        if ("true".equals(Poms.childText(activation, "activeByDefault"))) {
            parts.add("active by default");
        }
        String jdk = Poms.childText(activation, "jdk");
        if (jdk != null) {
            parts.add("jdk " + jdk);
        }
        Element property = Poms.child(activation, "property");
        if (property != null) {
            String value = Poms.childText(property, "value");
            parts.add("property " + Poms.childText(property, "name", "?") + (value == null ? "" : "=" + value));
        }
        Element file = Poms.child(activation, "file");
        if (file != null) {
            parts.add("file");
        }
        Element os = Poms.child(activation, "os");
        if (os != null) {
            parts.add("os");
        }
        return parts.isEmpty() ? "-P only" : String.join(", ", parts);
    }

    private String describeContents(Element profile) {
        List<String> parts = new ArrayList<>();
        count(parts, Poms.child(profile, "dependencies"), "dependency", "dependencies");
        count(parts, Poms.child(profile, "modules"), "module", "modules");
        count(parts, Poms.path(profile, "build", "plugins").orElse(null), "plugin", "plugins");
        Element properties = Poms.child(profile, "properties");
        if (properties != null && !properties.getChildren().isEmpty()) {
            parts.add(count(properties.getChildren().size(), "property", "properties"));
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    private void count(List<String> parts, Element container, String singular, String plural) {
        int size = Poms.children(container, singular).size();
        if (size > 0) {
            parts.add(count(size, singular, plural));
        }
    }

    private static String count(int size, String singular, String plural) {
        return size + " " + (size == 1 ? singular : plural);
    }
}
