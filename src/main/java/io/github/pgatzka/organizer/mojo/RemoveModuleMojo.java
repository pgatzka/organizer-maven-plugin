package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Removes a module from an aggregator POM.
 *
 * <pre>
 * mvn organizer:remove-module -Dmodule=my-service
 * </pre>
 *
 * <p>Only the {@code <module>} entry goes, unless {@code -DdeleteDirectory} is passed as well —
 * and even then the deletion is confirmed first.
 */
@Mojo(name = "remove-module", requiresProject = false, threadSafe = true)
public class RemoveModuleMojo extends AbstractPomWriteMojo {

    /** The module directory to remove from the list. */
    @Parameter(property = "module")
    String module;

    /** Also delete the module directory from disk. Off by default. */
    @Parameter(property = "deleteDirectory", defaultValue = "false")
    boolean deleteDirectory;

    /** Fail when the POM does not list the module. */
    @Parameter(property = "failIfMissing", defaultValue = "false")
    boolean failIfMissing;

    /** Remove the surrounding {@code <modules>} element when it is left empty. */
    @Parameter(property = "removeEmptyElements", defaultValue = "true")
    boolean removeEmptyElements = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element modules = Poms.child(targetElement(pom), "modules");
        if (modules == null || !Poms.hasElementChildren(modules)) {
            reportMissing("This POM lists no modules");
            return;
        }

        List<Element> declared = Poms.children(modules, "module");
        String name = module != null && !module.isBlank()
                ? module.trim()
                : declared.get(requireChoice(
                                "Which module should be removed?",
                                declared.stream().map(Element::getTextTrim).toList(),
                                "module"))
                        .getTextTrim();

        Element entry = declared.stream()
                .filter(candidate -> name.equals(candidate.getTextTrim()))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            reportMissing("No module named " + name + " in " + pomFile);
            return;
        }

        if (!confirm("Remove module " + name + " from the POM?")) {
            getLog().info("Left the POM unchanged");
            return;
        }

        if (removeEmptyElements) {
            Poms.removeAndPrune(entry);
        } else {
            Poms.remove(modules, entry);
        }
        getLog().info("Removed module " + name);

        if (deleteDirectory) {
            deleteDirectory(name);
        }
    }

    private void deleteDirectory(String name) throws MojoExecutionException {
        Path directory = pomDirectory().resolve(name);
        if (!Files.isDirectory(directory)) {
            getLog().info("No directory at " + directory + " to delete");
            return;
        }
        if (!confirm("Delete the directory " + directory + " and everything in it?")) {
            getLog().info("Kept the directory " + directory);
            return;
        }
        if (dryRun) {
            getLog().info("Dry run, not deleting " + directory);
            return;
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot delete " + directory + ": " + e.getMessage(), e);
        }
        getLog().info("Deleted " + directory);
    }

    private void reportMissing(String message) throws MojoFailureException {
        if (failIfMissing) {
            throw new MojoFailureException(message);
        }
        getLog().info(message + "; nothing to remove");
    }
}
