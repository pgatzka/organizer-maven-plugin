package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.PomSchema;
import io.github.pgatzka.organizer.core.Poms;
import io.github.pgatzka.organizer.core.Versions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Changes the project version, preserving the rest of the POM byte for byte.
 *
 * <pre>
 * mvn organizer:set-version -DnewVersion=1.2.0
 * mvn organizer:set-version -Dbump=minor
 * mvn organizer:set-version -DreleaseVersion          # 1.2.3-SNAPSHOT -&gt; 1.2.3
 * mvn organizer:set-version -DnextSnapshot            # 1.2.3          -&gt; 1.2.4-SNAPSHOT
 * mvn organizer:set-version -DnewVersion=2.0.0 -DupdateChildren
 * </pre>
 *
 * <p>{@code -DupdateChildren} walks {@code <modules>} and rewrites each child's
 * {@code <parent><version>}, recursively, so a reactor stays consistent.
 */
@Mojo(name = "set-version", requiresProject = false, threadSafe = true)
public class SetVersionMojo extends AbstractPomWriteMojo {

    /** The version to set. */
    @Parameter(property = "newVersion")
    String newVersion;

    /** Increment a segment of the current version: {@code major}, {@code minor} or {@code patch}. */
    @Parameter(property = "bump")
    String bump;

    /** Drop the {@code -SNAPSHOT} suffix from the current version. */
    @Parameter(property = "releaseVersion", defaultValue = "false")
    boolean releaseVersion;

    /** Increment the patch segment and add {@code -SNAPSHOT}. */
    @Parameter(property = "nextSnapshot", defaultValue = "false")
    boolean nextSnapshot;

    /** Also update {@code <parent><version>} when it matches the version being replaced. */
    @Parameter(property = "updateParent", defaultValue = "false")
    boolean updateParent;

    /** Also rewrite the {@code <parent><version>} of every module, recursively. */
    @Parameter(property = "updateChildren", defaultValue = "false")
    boolean updateChildren;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Element root = pom.getRoot();
        String current = currentVersion(root);
        String target = targetVersion(current);

        if (target.equals(current)) {
            getLog().info("Version is already " + target);
        } else {
            Poms.setChildTextOrdered(root, "version", target, PomSchema.PROJECT, pom.getIndentUnit());
            getLog().info("Set version to " + target + (current == null ? "" : " (was " + current + ")"));
        }

        if (updateParent) {
            updateParentVersion(root, current, target, pom.getIndentUnit());
        }
        if (updateChildren) {
            updateChildren(pom, current, target);
        }
    }

    /** The version the POM declares, falling back to the one it inherits. */
    private String currentVersion(Element root) throws MojoFailureException {
        String declared = Poms.childText(root, "version");
        if (declared != null) {
            return declared;
        }
        Element parent = Poms.child(root, "parent");
        String inherited = parent == null ? null : Poms.childText(parent, "version");
        if (inherited == null) {
            throw new MojoFailureException(
                    "This POM declares no version and inherits none, so there is nothing to change. "
                            + "Pass -DnewVersion=<version>.");
        }
        return inherited;
    }

    /** Works out what the new version should be from whichever option was given. */
    private String targetVersion(String current) throws MojoFailureException {
        int chosen = 0;
        if (newVersion != null && !newVersion.isBlank()) {
            chosen++;
        }
        if (bump != null && !bump.isBlank()) {
            chosen++;
        }
        if (releaseVersion) {
            chosen++;
        }
        if (nextSnapshot) {
            chosen++;
        }
        if (chosen > 1) {
            throw new MojoFailureException(
                    "Choose one of -DnewVersion, -Dbump, -DreleaseVersion or -DnextSnapshot, not several.");
        }

        try {
            if (newVersion != null && !newVersion.isBlank()) {
                return newVersion.trim();
            }
            if (bump != null && !bump.isBlank()) {
                return Versions.bump(current, Versions.Segment.parse(bump));
            }
            if (releaseVersion) {
                return Versions.release(current);
            }
            if (nextSnapshot) {
                return Versions.nextSnapshot(current);
            }
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        return require(null, "newVersion", "New version", current).trim();
    }

    private void updateParentVersion(Element root, String current, String target, String indent) {
        Element parent = Poms.child(root, "parent");
        if (parent == null) {
            return;
        }
        String parentVersion = Poms.childText(parent, "version");
        if (parentVersion == null || !parentVersion.equals(current)) {
            return;
        }
        Poms.setChildTextOrdered(parent, "version", target, PomSchema.PARENT, indent);
        getLog().info("Updated the parent version to " + target);
    }

    /** Rewrites the {@code <parent><version>} of every module that pointed at the old version. */
    private void updateChildren(PomDocument pom, String current, String target)
            throws MojoExecutionException {
        for (Path childPom : modulePoms(pom)) {
            PomDocument child;
            try {
                child = PomDocument.load(childPom);
            } catch (IOException | RuntimeException e) {
                throw new MojoExecutionException("Cannot read module POM " + childPom + ": " + e.getMessage(), e);
            }

            Element parent = Poms.child(child.getRoot(), "parent");
            if (parent != null && current != null && current.equals(Poms.childText(parent, "version"))) {
                Poms.setChildTextOrdered(parent, "version", target, PomSchema.PARENT, child.getIndentUnit());
                getLog().info("Updated the parent version in " + childPom);
            }

            updateChildren(child, current, target);
            save(child);
        }
    }

    /** The POM of every module listed, skipping the ones that are not there. */
    private List<Path> modulePoms(PomDocument pom) {
        Path directory = pom.getPath().toAbsolutePath().getParent();
        List<Path> poms = new ArrayList<>();
        for (Element module : Poms.children(Poms.child(pom.getRoot(), "modules"), "module")) {
            Path childPom = directory.resolve(module.getTextTrim()).resolve("pom.xml");
            if (Files.isRegularFile(childPom)) {
                poms.add(childPom);
            } else {
                getLog().warn("Module " + module.getTextTrim() + " has no POM at " + childPom);
            }
        }
        return poms;
    }
}
