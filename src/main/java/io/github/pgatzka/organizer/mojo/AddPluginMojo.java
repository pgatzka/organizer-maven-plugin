package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.PomDocument;
import io.github.pgatzka.organizer.core.Plugins;
import io.github.pgatzka.organizer.core.Poms;
import io.github.pgatzka.organizer.core.VersionResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jdom2.Element;

/**
 * Adds a build plugin.
 *
 * <pre>
 * mvn organizer:add-plugin -Dplugin=maven-surefire-plugin:3.5.2
 * mvn organizer:add-plugin -Dplugin=maven-surefire-plugin -Dconfiguration=skipTests=true
 * mvn organizer:add-plugin -Dplugin=maven-jar-plugin -Dexecutions=make-jar:package:jar
 * </pre>
 *
 * <p>The group defaults to {@code org.apache.maven.plugins}, and is left out of the written entry
 * when it is that default, matching what people write by hand. A plugin that is already declared
 * is merged into rather than duplicated: existing configuration settings the request does not
 * mention are left alone.
 */
@Mojo(name = "add-plugin", requiresProject = false, threadSafe = true)
public class AddPluginMojo extends AbstractPluginMojo {

    /** Configuration settings, as {@code key=value,key=value}. */
    @Parameter(property = "configuration")
    String configuration;

    /** Executions, as {@code id:phase:goal1+goal2,id:phase:goal}. */
    @Parameter(property = "executions")
    String executions;

    /** Consider {@code -SNAPSHOT} versions when resolving the newest one. */
    @Parameter(property = "allowSnapshots", defaultValue = "false")
    boolean allowSnapshots;

    /** Look the newest version up in the plugin repositories when none was given. */
    @Parameter(property = "organizer.resolveLatest", defaultValue = "true")
    boolean resolveLatest = true;

    @Override
    protected void run(PomDocument pom) throws MojoExecutionException, MojoFailureException {
        Coordinate coordinate = coordinate();
        Map<String, String> settings;
        List<Plugins.Execution> requestedExecutions;
        try {
            settings = Plugins.parseConfiguration(configuration);
            requestedExecutions = Plugins.Execution.parseAll(executions);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }

        Element target = targetElement(pom);
        Element container = Poms.path(target, sectionPath()).orElse(null);
        Optional<Element> existing = container == null ? Optional.empty() : Plugins.find(container, coordinate);

        if (existing.isPresent()) {
            if (Plugins.merge(existing.get(), coordinate, settings, requestedExecutions, pom.getIndentUnit())) {
                getLog().info("Updated plugin " + Plugins.describe(existing.get()));
            } else {
                getLog().info("Plugin " + coordinate.toGA() + " is already declared as requested");
            }
            return;
        }

        coordinate = withVersion(coordinate);
        Element element = Plugins.build(target, coordinate, settings, requestedExecutions);
        Poms.append(
                Poms.pathOrCreate(target, pom.getIndentUnit(), sectionPath()), element, pom.getIndentUnit());
        getLog().info("Added plugin " + Plugins.describe(element) + " to " + sectionName());
    }

    /**
     * Fills in a missing version from {@code <pluginManagement>} or the plugin repositories.
     */
    private Coordinate withVersion(Coordinate coordinate) throws MojoExecutionException {
        if (coordinate.hasVersion() || !resolveLatest) {
            return coordinate;
        }
        VersionResolver resolver = versionResolver();
        if (resolver == null) {
            return coordinate;
        }
        if (session != null && session.isOffline()) {
            throw new MojoExecutionException(
                    "Cannot look up a version for " + coordinate.toGA()
                            + " while Maven is offline. Pass a version as -Dplugin=g:a:version, or drop -o.");
        }
        try {
            Optional<String> latest = resolver.latestVersion(coordinate, allowSnapshots);
            latest.ifPresent(version -> getLog().info(
                    "Resolved the newest version of " + coordinate.toGA() + " to " + version));
            return latest.map(coordinate::withVersion).orElse(coordinate);
        } catch (VersionResolver.VersionResolutionException e) {
            throw new MojoExecutionException(
                    e.getMessage() + ". Pass a version as -Dplugin=g:a:version to skip the lookup.", e);
        }
    }
}
