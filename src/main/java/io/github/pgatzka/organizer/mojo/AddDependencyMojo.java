package io.github.pgatzka.organizer.mojo;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Adds a dependency to the POM.
 *
 * <pre>
 * mvn organizer:add-dependency -Dartifact=org.junit.jupiter:junit-jupiter:5.11.4 -Dscope=test
 * </pre>
 *
 * <p>The version may be left out when {@code dependencyManagement}, an imported BOM or a parent
 * POM already manages it. An entry that is already present is updated in place rather than
 * duplicated.
 */
@Mojo(name = "add-dependency", requiresProject = false, threadSafe = true)
public class AddDependencyMojo extends AbstractAddDependencyMojo {

    @Override
    String[] sectionPath() {
        return new String[] {"dependencies"};
    }

    @Override
    String entryName() {
        return "dependency";
    }

    @Override
    String sectionName() {
        return "dependencies";
    }
}
