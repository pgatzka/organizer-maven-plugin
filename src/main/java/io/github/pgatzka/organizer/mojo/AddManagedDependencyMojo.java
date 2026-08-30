package io.github.pgatzka.organizer.mojo;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Adds an entry to {@code <dependencyManagement>}, pinning a version for the whole build without
 * putting the dependency on any classpath.
 *
 * <pre>
 * mvn organizer:add-managed-dependency -Dartifact=com.google.guava:guava:33.0.0-jre
 * </pre>
 *
 * <p>Once an entry is managed here, {@code organizer:add-dependency} can add the dependency
 * without a version.
 */
@Mojo(name = "add-managed-dependency", requiresProject = false, threadSafe = true)
public class AddManagedDependencyMojo extends AbstractAddDependencyMojo {

    @Override
    String[] sectionPath() {
        return new String[] {"dependencyManagement", "dependencies"};
    }

    @Override
    String entryName() {
        return "managed dependency";
    }

    @Override
    String sectionName() {
        return "dependencyManagement";
    }
}
