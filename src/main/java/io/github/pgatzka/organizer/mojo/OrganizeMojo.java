package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDiff;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.PomOrganizer;
import java.util.Comparator;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Puts the POM in order: sections in the sequence the POM reference recommends, dependencies,
 * plugins, modules and properties sorted.
 *
 * <pre>
 * mvn organizer:organize
 * mvn organizer:organize -DsortDependencies=scope,groupId,artifactId
 * mvn organizer:organize -DcheckOnly            # fails when the POM is not organized
 * </pre>
 *
 * <p>Comments travel with the element they sit above, and blank lines between entries are kept, so
 * an organized POM still reads the way it was written. Running the goal twice changes nothing the
 * second time.
 */
@Mojo(name = "organize", defaultPhase = LifecyclePhase.VALIDATE, requiresProject = false, threadSafe = true)
public class OrganizeMojo extends AbstractPomWriteMojo {

    /** Put the top-level sections in the order the POM reference recommends. */
    @Parameter(property = "reorderSections", defaultValue = "true")
    boolean reorderSections = true;

    /**
     * How to sort dependencies: a comma-separated list of {@code <dependency>} child elements, such
     * as {@code scope,groupId,artifactId}. Pass {@code false} to leave them in place.
     */
    @Parameter(property = "sortDependencies", defaultValue = "groupId,artifactId")
    String sortDependencies;

    /** Sort {@code <plugins>} by coordinate. */
    @Parameter(property = "sortPlugins", defaultValue = "true")
    boolean sortPlugins = true;

    /** Sort {@code <modules>} alphabetically. */
    @Parameter(property = "sortModules", defaultValue = "true")
    boolean sortModules = true;

    /** Sort {@code <properties>} by name. */
    @Parameter(property = "sortProperties", defaultValue = "true")
    boolean sortProperties = true;

    /** Keep the blank lines that separate entries. */
    @Parameter(property = "keepBlankLines", defaultValue = "true")
    boolean keepBlankLines = true;

    /** Report whether the POM is organized and fail if it is not, without writing anything. */
    @Parameter(property = "checkOnly", defaultValue = "false")
    boolean checkOnly;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Comparator<Element> dependencyOrder;
        try {
            dependencyOrder = PomOrganizer.Options.dependencyOrder(sortDependencies);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        new PomOrganizer(new PomOrganizer.Options(
                        reorderSections,
                        dependencyOrder,
                        sortPlugins,
                        sortModules,
                        sortProperties,
                        keepBlankLines))
                .organize(pom);

        if (checkOnly) {
            check(pom);
        }
    }

    /** In check mode the goal reports rather than writes, so nothing reaches {@link #save}. */
    private void check(PomDocument pom) throws MojoFailureException {
        if (!pom.isModified()) {
            getLog().info(pomFile + " is organized");
            return;
        }
        List<String> diff = PomDiff.of(pom);
        for (String line : diff) {
            getLog().error(line);
        }
        throw new MojoFailureException(
                pomFile + " is not organized. Run mvn organizer:organize to fix it.");
    }

    @Override
    protected void save(PomDocument pom) throws MojoExecutionException {
        if (checkOnly) {
            return;
        }
        super.save(pom);
    }
}
