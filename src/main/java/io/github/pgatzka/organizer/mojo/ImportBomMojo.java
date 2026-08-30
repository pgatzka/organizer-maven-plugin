package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Dependencies;
import io.github.pgatzka.organizer.core.DependencyOptions;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Poms;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Imports a bill of materials into {@code <dependencyManagement>}.
 *
 * <pre>
 * mvn organizer:import-bom -Dbom=org.springframework.boot:spring-boot-dependencies:3.2.0
 * </pre>
 *
 * <p>Writes the managed entry with {@code <type>pom</type>} and {@code <scope>import</scope>}.
 * Importing a BOM that is already there updates its version rather than adding a second entry, and
 * dependencies the BOM manages can then be added without a version.
 */
@Mojo(name = "import-bom", requiresProject = false, threadSafe = true)
public class ImportBomMojo extends AbstractDependencyMojo {

    /** The BOM, as {@code groupId:artifactId:version}. */
    @Parameter(property = "bom")
    String bom;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Coordinate coordinate = bomCoordinate();
        Element target = targetElement(pom);
        Element container = managementContainer(target);

        Optional<Element> existing =
                container == null ? Optional.empty() : Dependencies.find(container, coordinate);
        DependencyOptions options = DependencyOptions.ofScope("import");

        if (existing.isPresent()) {
            if (Dependencies.update(existing.get(), coordinate, options, pom.getIndentUnit())) {
                getLog().info("Updated imported BOM " + coordinate);
            } else {
                getLog().info("BOM " + coordinate + " is already imported");
            }
            return;
        }

        coordinate = resolveVersion(pom, target, coordinate);
        Element entry = Dependencies.build(target, coordinate, options);
        Poms.append(
                Poms.pathOrCreate(target, pom.getIndentUnit(), "dependencyManagement", "dependencies"),
                entry,
                pom.getIndentUnit());
        getLog().info("Imported BOM " + coordinate);
    }

    /** The BOM coordinate, always with {@code type=pom}. */
    private Coordinate bomCoordinate() throws MojoFailureException {
        if (bom != null && !bom.isBlank()) {
            artifact = bom;
        }
        Coordinate coordinate = coordinate();
        return new Coordinate(
                coordinate.getGroupId(), coordinate.getArtifactId(), coordinate.getVersion(), null, "pom");
    }
}
