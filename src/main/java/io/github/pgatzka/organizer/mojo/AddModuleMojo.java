package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.PomSchema;
import io.github.pgatzka.organizer.core.Poms;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Adds a module to an aggregator POM.
 *
 * <pre>
 * mvn organizer:add-module -Dmodule=my-service
 * mvn organizer:add-module -Dmodule=my-service -Dscaffold
 * </pre>
 *
 * <p>Keeps {@code <modules>} sorted when it already is, switches the aggregator's packaging to
 * {@code pom} when it is still {@code jar}, and can scaffold the child directory with a POM that
 * inherits from this one.
 */
@Mojo(name = "add-module", requiresProject = false, threadSafe = true)
public class AddModuleMojo extends AbstractPomWriteMojo {

    /** The module directory, relative to the aggregator. */
    @Parameter(property = "module")
    String module;

    /** Switch the aggregator's packaging to {@code pom} when it is not already. */
    @Parameter(property = "updatePackaging", defaultValue = "true")
    boolean updatePackaging = true;

    /** Create the module directory with a minimal POM inheriting from the aggregator. */
    @Parameter(property = "scaffold", defaultValue = "false")
    boolean scaffold;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        String name = require(module, "module", "Module directory", null).trim();
        Element target = targetElement(pom);
        // Set the packaging first: a <packaging> created afterwards would land after <modules>,
        // which is the wrong way round for the POM schema.
        updatePackaging(pom);
        Element modules = Poms.childOrCreate(target, "modules", pom.getIndentUnit());

        boolean alreadyThere = Poms.children(modules, "module").stream()
                .anyMatch(entry -> name.equals(entry.getTextTrim()));
        if (alreadyThere) {
            getLog().info("Module " + name + " is already listed");
        } else {
            Poms.insertSorted(
                    modules,
                    Poms.element(target, "module", name),
                    "module",
                    Comparator.comparing(Element::getTextTrim),
                    pom.getIndentUnit());
            getLog().info("Added module " + name);
        }

        if (scaffold) {
            scaffold(pom, name);
        }
    }

    /** An aggregator has to be packaged as {@code pom} for its modules to build. */
    private void updatePackaging(PomDocument pom) {
        if (!updatePackaging) {
            return;
        }
        Element root = pom.getRoot();
        String packaging = Poms.childText(root, "packaging", "jar");
        if ("pom".equals(packaging)) {
            return;
        }
        Poms.setChildTextOrdered(root, "packaging", "pom", PomSchema.PROJECT, pom.getIndentUnit());
        getLog().info("Changed packaging from " + packaging + " to pom");
    }

    /** Writes a minimal child POM, unless the directory already has one. */
    private void scaffold(PomDocument pom, String name) throws MojoExecutionException {
        Path directory = pomDirectory().resolve(name);
        Path childPom = directory.resolve("pom.xml");
        if (Files.exists(childPom)) {
            getLog().info("Leaving the existing POM in " + name + " alone");
            return;
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(childPom, childPom(pom, name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot scaffold " + childPom + ": " + e.getMessage(), e);
        }
        getLog().info("Scaffolded " + childPom);
    }

    private String childPom(PomDocument pom, String name) {
        Element root = pom.getRoot();
        Element parent = Poms.child(root, "parent");
        String groupId = Poms.childText(root, "groupId", parent == null ? null : Poms.childText(parent, "groupId"));
        String artifactId = Poms.childText(root, "artifactId", "parent");
        String version = Poms.childText(root, "version", parent == null ? null : Poms.childText(parent, "version"));
        String namespace = pom.getNamespace().getURI();
        String indent = pom.getIndentUnit();

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<project");
        if (!namespace.isEmpty()) {
            xml.append(" xmlns=\"").append(namespace).append('"');
        }
        xml.append(">\n");
        xml.append(indent).append("<modelVersion>4.0.0</modelVersion>\n\n");
        xml.append(indent).append("<parent>\n");
        if (groupId != null) {
            xml.append(indent.repeat(2)).append("<groupId>").append(groupId).append("</groupId>\n");
        }
        xml.append(indent.repeat(2)).append("<artifactId>").append(artifactId).append("</artifactId>\n");
        if (version != null) {
            xml.append(indent.repeat(2)).append("<version>").append(version).append("</version>\n");
        }
        xml.append(indent).append("</parent>\n\n");
        xml.append(indent).append("<artifactId>").append(name).append("</artifactId>\n");
        xml.append("</project>\n");
        return xml.toString();
    }
}
