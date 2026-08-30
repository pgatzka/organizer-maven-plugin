package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.ConsolePrompter;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import io.github.pgatzka.organizer.core.Prompter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import io.github.pgatzka.organizer.core.VersionResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jdom2.Element;

/**
 * Shared plumbing for every goal: locating the POM, optional profile targeting and interactive
 * prompting.
 */
public abstract class AbstractPomMojo extends AbstractMojo {

    /** The POM to work on. Defaults to the {@code pom.xml} in the current directory. */
    @Parameter(property = "organizer.pom", defaultValue = "${basedir}/pom.xml")
    File pomFile;

    /**
     * Operate inside this profile instead of at the top level of the POM. Applies to the
     * dependency, property, plugin and repository goals.
     */
    @Parameter(property = "profile")
    String profile;

    /** Create the profile named by {@code -Dprofile} when the POM does not declare it yet. */
    @Parameter(property = "createProfile", defaultValue = "false")
    boolean createProfile;

    /**
     * Ask for missing parameters instead of failing. Defaults to whether Maven itself is running
     * interactively, so {@code mvn -B} never blocks on input.
     */
    @Parameter(property = "organizer.interactive")
    Boolean interactive;

    @Parameter(defaultValue = "${session}", readonly = true)
    MavenSession session;

    @Component
    RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    List<RemoteRepository> remoteRepositories;

    private Prompter prompter;
    private VersionResolver versionResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        PomDocument pom = loadPom();
        run(pom);
    }

    /** Does the work of the goal. */
    protected abstract void run(PomDocument pom) throws MojoExecutionException, MojoFailureException;

    // ---------------------------------------------------------------- helpers

    protected PomDocument loadPom() throws MojoExecutionException {
        if (pomFile == null || !pomFile.isFile()) {
            throw new MojoExecutionException(
                    "No POM found at " + pomFile + ". Pass -Dorganizer.pom=<path> to point at one.");
        }
        try {
            return PomDocument.load(pomFile.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read " + pomFile + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Cannot parse " + pomFile + ": " + e.getMessage(), e);
        }
    }

    /** The directory the POM lives in, which module paths are resolved against. */
    Path pomDirectory() throws MojoExecutionException {
        Path parent = pomFile.toPath().toAbsolutePath().getParent();
        if (parent == null) {
            throw new MojoExecutionException(pomFile + " has no parent directory to resolve modules against.");
        }
        return parent;
    }

    /**
     * The element the goal should write into: the {@code <project>} element, or the requested
     * {@code <profile>} when {@code -Dprofile} was given.
     */
    protected Element targetElement(PomDocument pom) throws MojoExecutionException {
        Element root = pom.getRoot();
        if (profile == null || profile.isBlank()) {
            return root;
        }
        Element profiles = Poms.child(root, "profiles");
        if (profiles != null) {
            for (Element candidate : Poms.children(profiles, "profile")) {
                if (profile.equals(Poms.childText(candidate, "id"))) {
                    return candidate;
                }
            }
        }
        if (!createProfile) {
            throw new MojoExecutionException(
                    "No profile with id '" + profile + "' in " + pomFile
                            + ". Add -DcreateProfile=true to create it.");
        }
        Element created = createProfile(pom, root);
        getLog().info("Created profile '" + profile + "'");
        return created;
    }

    private Element createProfile(PomDocument pom, Element root) {
        String indent = pom.getIndentUnit();
        Element profiles = Poms.childOrCreate(root, "profiles", indent);
        Element created = Poms.element(root, "profile");
        created.addContent(Poms.element(root, "id", profile));
        Poms.append(profiles, created, indent);
        return created;
    }

    /** Whether this run may ask the user questions. */
    protected boolean isInteractive() {
        if (interactive != null) {
            return interactive;
        }
        return session != null && session.getRequest() != null && session.getRequest().isInteractiveMode();
    }

    /** The prompter for this run; never {@code null}, but may refuse to prompt. */
    protected Prompter prompter() {
        if (prompter == null) {
            prompter = new ConsolePrompter(
                    new InputStreamReader(System.in, Charset.defaultCharset()), System.out, isInteractive());
        }
        return prompter;
    }

    /** Test seam: replaces the console prompter with a scripted one. */
    void setPrompter(Prompter prompter) {
        this.prompter = prompter;
    }

    /**
     * Looks versions up in the repositories Maven is configured with, falling back to Maven Central
     * when no project is loaded. Returns {@code null} when the Maven runtime did not supply a
     * repository system, as it does not in plain unit tests.
     */
    VersionResolver versionResolver() {
        if (versionResolver == null && repositorySystem != null && repositorySession != null) {
            versionResolver = new VersionResolver(repositorySystem, repositorySession, repositoriesToSearch());
        }
        return versionResolver;
    }

    /** Which repositories {@link #versionResolver()} should search. */
    List<RemoteRepository> repositoriesToSearch() {
        return remoteRepositories;
    }

    /** Test seam: replaces the repository-backed resolver. */
    void setVersionResolver(VersionResolver versionResolver) {
        this.versionResolver = versionResolver;
    }

    /**
     * Returns {@code value} when it was supplied, otherwise asks for it, otherwise fails with an
     * actionable message.
     */
    protected String require(String value, String parameterName, String question, String defaultValue)
            throws MojoFailureException {
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (prompter().isInteractive()) {
            return prompter().prompt(question, defaultValue);
        }
        throw new MojoFailureException(
                "Missing required parameter '" + parameterName + "'. Pass -D" + parameterName
                        + "=<value>, or run without -B to be asked for it.");
    }

    /** Asks the user to pick one of {@code labels}, or fails when the session is not interactive. */
    protected int requireChoice(String question, List<String> labels, String parameterName)
            throws MojoFailureException {
        if (labels.isEmpty()) {
            throw new MojoFailureException("Nothing to choose from: " + question);
        }
        if (prompter().isInteractive()) {
            return prompter().select(question, labels, 0);
        }
        throw new MojoFailureException(
                "Missing required parameter '" + parameterName + "'. Pass -D" + parameterName
                        + "=<value>, or run without -B to choose from a list.");
    }
}
