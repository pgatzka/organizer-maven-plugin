package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Removes a repository from the POM.
 *
 * <pre>
 * mvn organizer:remove-repository -Did=internal
 * </pre>
 *
 * <p>Run without {@code -Did} to pick from the repositories the POM declares.
 */
@Mojo(name = "remove-repository", requiresProject = false, threadSafe = true)
public class RemoveRepositoryMojo extends AbstractRepositoryMojo {

    /** Fail when the POM does not declare the repository. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding section element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element target = targetElement(pom);
        if (declared(target).isEmpty()) {
            reportMissing("This POM declares no " + sectionName());
            return;
        }

        String repositoryId = id != null && !id.isBlank() ? id : chooseExistingId(target);
        Element repository = findById(target, repositoryId);
        if (repository == null) {
            reportMissing("No " + entryName() + " with id " + repositoryId + " in " + pomFile);
            return;
        }

        if (!confirm("Remove " + entryName() + " " + repositoryId + "?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        if (removeEmptyElements) {
            Poms.removeAndPrune(repository);
        } else {
            Poms.remove(repository.getParentElement(), repository);
        }
        getLog().info("Removed " + entryName() + " " + repositoryId);
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
