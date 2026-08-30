package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.DependencyReport;
import io.github.pgatzka.organizer.core.PomDocument;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Prints the dependencies the POM declares. Read-only: the file is never written.
 *
 * <pre>
 * mvn organizer:list-dependencies
 * mvn organizer:list-dependencies -Dscope=test -Dformat=table
 * mvn organizer:list-dependencies -Dfilter=org.springframework.* -DincludeManaged
 * </pre>
 *
 * <p>This is what the POM says, not the resolved dependency graph; use
 * {@code dependency:tree} for the latter.
 */
@Mojo(name = "list-dependencies", requiresProject = false, threadSafe = true)
public class ListDependenciesMojo extends AbstractPomMojo {

    /** Show only this scope. */
    @Parameter(property = "scope")
    String scope;

    /** Show only coordinates matching {@code groupId:artifactId}, where {@code *} is a wildcard. */
    @Parameter(property = "filter")
    String filter;

    /** Layout: {@code plain}, {@code table} or {@code tree}. */
    @Parameter(property = "format", defaultValue = "plain")
    String format;

    /** Also list {@code <dependencyManagement>} entries. */
    @Parameter(property = "includeManaged", defaultValue = "false")
    boolean includeManaged;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        DependencyReport.Format layout;
        Coordinate pattern;
        try {
            layout = DependencyReport.Format.parse(format);
            pattern = filter == null || filter.isBlank() ? null : Coordinate.parse(filter);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        List<DependencyReport.Entry> entries =
                DependencyReport.collect(targetElement(pom), includeManaged);
        for (String line : DependencyReport.render(DependencyReport.filter(entries, scope, pattern), layout)) {
            getLog().info(line);
        }
    }
}
