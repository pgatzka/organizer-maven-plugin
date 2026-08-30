package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Poms;
import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/** Shared parameters for the repository goals. */
abstract class AbstractRepositoryMojo extends AbstractPomWriteMojo {

    /** The repository id, which is also what settings.xml matches credentials against. */
    @Parameter(property = "id")
    String id;

    /** Work on {@code <pluginRepositories>} instead of {@code <repositories>}. */
    @Parameter(property = "pluginRepository", defaultValue = "false")
    boolean pluginRepository;

    /** The name of the section this goal works on. */
    String sectionName() {
        return pluginRepository ? "pluginRepositories" : "repositories";
    }

    /** The name of one entry in that section. */
    String entryName() {
        return pluginRepository ? "pluginRepository" : "repository";
    }

    /** The repositories declared in the POM. */
    List<Element> declared(Element target) {
        return Poms.children(Poms.child(target, sectionName()), entryName());
    }

    /** Finds a declared repository by id. */
    Element findById(Element target, String repositoryId) {
        for (Element repository : declared(target)) {
            if (repositoryId.equals(Poms.childText(repository, "id"))) {
                return repository;
            }
        }
        return null;
    }

    /** Lets the user pick from the repositories already declared. */
    String chooseExistingId(Element target) throws MojoFailureException {
        List<Element> existing = declared(target);
        List<String> labels = existing.stream()
                .map(repository -> Poms.childText(repository, "id", "(no id)") + "  "
                        + Poms.childText(repository, "url", ""))
                .toList();
        int chosen = requireChoice("Which " + entryName() + " should be removed?", labels, "id");
        return Poms.childText(existing.get(chosen), "id");
    }
}
