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
 * Adds a repository to the POM.
 *
 * <pre>
 * mvn organizer:add-repository -Did=internal -Durl=https://repo.example.com/maven2
 * mvn organizer:add-repository -Did=plugins -Durl=... -DpluginRepository
 * </pre>
 *
 * <p>Adding an id that is already declared updates that entry rather than adding a second one.
 */
@Mojo(name = "add-repository", requiresProject = false, threadSafe = true)
public class AddRepositoryMojo extends AbstractRepositoryMojo {

    /** The repository URL. */
    @Parameter(property = "url")
    String url;

    /** A human-readable name for the repository. */
    @Parameter(property = "name")
    String name;

    /** The repository layout, when it is not {@code default}. */
    @Parameter(property = "layout")
    String layout;

    /** Whether release artifacts may be downloaded from here. */
    @Parameter(property = "releases")
    Boolean releases;

    /** Whether snapshot artifacts may be downloaded from here. */
    @Parameter(property = "snapshots")
    Boolean snapshots;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        String repositoryId = require(id, "id", "Repository id", null);
        String repositoryUrl = require(url, "url", "Repository URL", currentUrl(pom, repositoryId));

        Element target = targetElement(pom);
        Element existing = findById(target, repositoryId);
        String indent = pom.getIndentUnit();

        if (existing != null) {
            update(existing, repositoryUrl, indent);
            getLog().info("Updated " + entryName() + " " + repositoryId);
            return;
        }

        Element repository = build(target, repositoryId, repositoryUrl);
        Poms.append(Poms.childOrCreate(target, sectionName(), indent), repository, indent);
        getLog().info("Added " + entryName() + " " + repositoryId + " (" + repositoryUrl + ")");
    }

    /**
     * Builds a detached entry, adding the children in schema order.
     *
     * <p>Plain {@code addContent} rather than the indentation-aware setters: the element has no
     * parent yet, so there is no depth to indent against. {@link Poms#append} lays the whole
     * subtree out when it goes in.
     */
    private Element build(Element context, String repositoryId, String repositoryUrl) {
        Element repository = Poms.element(context, entryName());
        repository.addContent(Poms.element(context, "id", repositoryId));
        if (isSet(name)) {
            repository.addContent(Poms.element(context, "name", name));
        }
        repository.addContent(Poms.element(context, "url", repositoryUrl));
        if (isSet(layout)) {
            repository.addContent(Poms.element(context, "layout", layout));
        }
        if (releases != null) {
            repository.addContent(policy(context, "releases", releases));
        }
        if (snapshots != null) {
            repository.addContent(policy(context, "snapshots", snapshots));
        }
        return repository;
    }

    /** Writes every value that was supplied into an entry already in the document. */
    private void update(Element repository, String repositoryUrl, String indent) {
        set(repository, "name", name, indent);
        set(repository, "url", repositoryUrl, indent);
        set(repository, "layout", layout, indent);
        setPolicy(repository, "releases", releases, indent);
        setPolicy(repository, "snapshots", snapshots, indent);
    }

    private void set(Element repository, String element, String value, String indent) {
        if (isSet(value)) {
            Poms.setChildTextOrdered(repository, element, value, PomSchema.REPOSITORY, indent);
        }
    }

    private void setPolicy(Element repository, String element, Boolean value, String indent) {
        if (value != null) {
            Poms.setChildOrdered(repository, policy(repository, element, value), PomSchema.REPOSITORY, indent);
        }
    }

    /** {@code <releases>} and {@code <snapshots>} wrap the flag in an {@code <enabled>} element. */
    private static Element policy(Element context, String element, boolean enabled) {
        Element wrapper = Poms.element(context, element);
        wrapper.addContent(Poms.element(context, "enabled", Boolean.toString(enabled)));
        return wrapper;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /** The repository's current URL, offered as the default when prompting. */
    private String currentUrl(PomDocument pom, String repositoryId) {
        Element existing = findById(pom.getRoot(), repositoryId);
        return existing == null ? null : Poms.childText(existing, "url");
    }
}
