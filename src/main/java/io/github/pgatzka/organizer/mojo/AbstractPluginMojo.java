package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Coordinate;
import io.github.pgatzka.organizer.core.Plugins;
import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.repository.RemoteRepository;
import org.jdom2.Element;

/** Shared parameters for the plugin goals. */
abstract class AbstractPluginMojo extends AbstractPomWriteMojo {

    /** The plugin, as {@code [groupId:]artifactId[:version]}. The group defaults to Maven's own. */
    @Parameter(property = "plugin")
    String plugin;

    /** Write into {@code <build><pluginManagement>} instead of {@code <build><plugins>}. */
    @Parameter(property = "pluginManagement", defaultValue = "false")
    boolean pluginManagement;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    List<RemoteRepository> remotePluginRepositories;

    /** Plugins live in the plugin repositories, not the project ones. */
    @Override
    List<RemoteRepository> repositoriesToSearch() {
        return remotePluginRepositories == null || remotePluginRepositories.isEmpty()
                ? super.repositoriesToSearch()
                : remotePluginRepositories;
    }

    /** The chain of element names leading to the {@code <plugins>} the goal works on. */
    String[] sectionPath() {
        return pluginManagement
                ? new String[] {"build", "pluginManagement", "plugins"}
                : new String[] {"build", "plugins"};
    }

    /** What to call the section in messages. */
    String sectionName() {
        return pluginManagement ? "pluginManagement" : "build plugins";
    }

    /** The plugin coordinate, asked for interactively when it was not given. */
    Coordinate coordinate() throws MojoFailureException {
        String text = require(plugin, "plugin", "Plugin ([groupId:]artifactId[:version])", null);
        try {
            return Plugins.parseCoordinate(text);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    /** Lets the user pick from the plugins already declared. */
    Coordinate chooseExistingPlugin(Element container) throws MojoFailureException {
        List<Element> existing = io.github.pgatzka.organizer.core.Poms.children(container, "plugin");
        if (existing.isEmpty()) {
            throw new MojoFailureException("This POM declares no plugins to choose from.");
        }
        List<String> labels = existing.stream().map(Plugins::describe).toList();
        return Plugins.coordinateOf(existing.get(requireChoice("Which plugin should be removed?", labels, "plugin")));
    }
}
