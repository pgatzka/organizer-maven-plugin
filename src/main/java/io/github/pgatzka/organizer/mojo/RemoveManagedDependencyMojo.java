package io.github.pgatzka.organizer.mojo;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Removes an entry from {@code <dependencyManagement>}.
 *
 * <pre>
 * mvn organizer:remove-managed-dependency -Dartifact=com.google.guava:guava
 * </pre>
 *
 * <p>Run without {@code -Dartifact} to pick from the managed entries. This also removes an
 * imported BOM, which is just a managed entry with {@code <scope>import</scope>}.
 */
@Mojo(name = "remove-managed-dependency", requiresProject = false, threadSafe = true)
public class RemoveManagedDependencyMojo extends AbstractRemoveDependencyMojo {

    @Override
    String[] sectionPath() {
        return new String[] {"dependencyManagement", "dependencies"};
    }

    @Override
    String entryName() {
        return "managed dependency";
    }
}
