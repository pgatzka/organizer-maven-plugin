package io.github.pgatzka.organizer.mojo;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Removes a dependency from the POM.
 *
 * <pre>
 * mvn organizer:remove-dependency -Dartifact=org.junit.jupiter:junit-jupiter
 * </pre>
 *
 * <p>Run without {@code -Dartifact} to pick from the dependencies the POM declares. The version is
 * ignored when matching, and {@code *} works as a wildcard in either coordinate segment.
 */
@Mojo(name = "remove-dependency", requiresProject = false, threadSafe = true)
public class RemoveDependencyMojo extends AbstractRemoveDependencyMojo {

    @Override
    String[] sectionPath() {
        return new String[] {"dependencies"};
    }

    @Override
    String entryName() {
        return "dependency";
    }
}
