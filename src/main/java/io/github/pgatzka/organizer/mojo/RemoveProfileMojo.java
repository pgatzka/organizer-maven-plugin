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
 * Removes a build profile and everything declared inside it.
 *
 * <pre>
 * mvn organizer:remove-profile -Dprofile=ci
 * </pre>
 *
 * <p>Run without {@code -Dprofile} to pick from the profiles the POM declares.
 */
@Mojo(name = "remove-profile", requiresProject = false, threadSafe = true)
public class RemoveProfileMojo extends AbstractPomWriteMojo {

    /** Fail when the POM does not declare the profile. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding {@code <profiles>} element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element profiles = Poms.child(pom.getRoot(), "profiles");
        List<Element> declared = Poms.children(profiles, "profile");
        if (declared.isEmpty()) {
            reportMissing("This POM declares no profiles");
            return;
        }

        String id = profile != null && !profile.isBlank()
                ? profile.trim()
                : Poms.childText(
                        declared.get(requireChoice(
                                "Which profile should be removed?",
                                declared.stream().map(entry -> Poms.childText(entry, "id", "(no id)")).toList(),
                                "profile")),
                        "id");

        Element target = declared.stream()
                .filter(candidate -> id != null && id.equals(Poms.childText(candidate, "id")))
                .findFirst()
                .orElse(null);
        if (target == null) {
            reportMissing("No profile with id " + id + " in " + pomFile);
            return;
        }

        if (!confirm("Remove profile " + id + " and everything declared inside it?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        if (removeEmptyElements) {
            Poms.removeAndPrune(target);
        } else {
            Poms.remove(profiles, target);
        }
        getLog().info("Removed profile " + id);
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
