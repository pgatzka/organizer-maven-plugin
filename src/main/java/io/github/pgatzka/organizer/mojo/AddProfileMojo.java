package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.PomSchema;
import io.github.pgatzka.organizer.core.Poms;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Adds a build profile.
 *
 * <pre>
 * mvn organizer:add-profile -Dprofile=ci
 * mvn organizer:add-profile -Dprofile=ci -DactiveByDefault
 * mvn organizer:add-profile -Dprofile=release -DactivationProperty=performRelease=true
 * mvn organizer:add-profile -Dprofile=modern -DjdkActivation=[17,)
 * </pre>
 *
 * <p>Once a profile exists, the other goals can write into it: pass {@code -Dprofile=<id>} to
 * {@code add-dependency}, {@code set-property}, {@code add-plugin} or {@code add-repository}.
 */
@Mojo(name = "add-profile", requiresProject = false, threadSafe = true)
public class AddProfileMojo extends AbstractPomWriteMojo {

    /** Activate the profile unless another one is selected. */
    @Parameter(property = "activeByDefault", defaultValue = "false")
    boolean activeByDefault;

    /** Activate on a property, as {@code name} or {@code name=value}. */
    @Parameter(property = "activationProperty")
    String activationProperty;

    /** Activate on a JDK version or range, e.g. {@code 17} or {@code [17,)}. */
    @Parameter(property = "jdkActivation")
    String jdkActivation;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        String id = require(profile, "profile", "Profile id", null).trim();
        String indent = pom.getIndentUnit();
        Element root = pom.getRoot();

        Element existing = findProfile(root, id);
        if (existing != null) {
            if (applyActivation(existing, indent)) {
                getLog().info("Updated the activation of profile " + id);
            } else {
                getLog().info("Profile " + id + " already exists");
            }
            return;
        }

        Element created = Poms.element(root, "profile");
        created.addContent(Poms.element(root, "id", id));
        Element activation = buildActivation(root);
        if (activation != null) {
            created.addContent(activation);
        }
        Poms.append(Poms.childOrCreate(root, "profiles", indent), created, indent);
        getLog().info("Added profile " + id);
    }

    private Element findProfile(Element root, String id) {
        for (Element candidate : Poms.children(Poms.child(root, "profiles"), "profile")) {
            if (id.equals(Poms.childText(candidate, "id"))) {
                return candidate;
            }
        }
        return null;
    }

    /** Builds a detached {@code <activation>}, or {@code null} when nothing was asked for. */
    private Element buildActivation(Element context) {
        if (!hasActivation()) {
            return null;
        }
        Element activation = Poms.element(context, "activation");
        if (activeByDefault) {
            activation.addContent(Poms.element(context, "activeByDefault", "true"));
        }
        if (jdkActivation != null && !jdkActivation.isBlank()) {
            activation.addContent(Poms.element(context, "jdk", jdkActivation.trim()));
        }
        if (activationProperty != null && !activationProperty.isBlank()) {
            activation.addContent(buildPropertyActivation(context));
        }
        return activation;
    }

    private Element buildPropertyActivation(Element context) {
        String text = activationProperty.trim();
        int equals = text.indexOf('=');
        Element property = Poms.element(context, "property");
        property.addContent(
                Poms.element(context, "name", equals < 0 ? text : text.substring(0, equals).trim()));
        if (equals >= 0) {
            property.addContent(Poms.element(context, "value", text.substring(equals + 1).trim()));
        }
        return property;
    }

    /** Replaces the activation of an existing profile. Returns whether anything changed. */
    private boolean applyActivation(Element existing, String indent) {
        Element activation = buildActivation(existing);
        if (activation == null) {
            return false;
        }
        Poms.setChildOrdered(existing, activation, PomSchema.PROFILE, indent);
        return true;
    }

    private boolean hasActivation() {
        return activeByDefault
                || (jdkActivation != null && !jdkActivation.isBlank())
                || (activationProperty != null && !activationProperty.isBlank());
    }
}
