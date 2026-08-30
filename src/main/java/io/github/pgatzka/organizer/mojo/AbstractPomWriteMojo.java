package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.PomDiff;
import io.github.pgatzka.organizer.core.PomDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for the goals that change the POM.
 *
 * <p>Adds the safety net every mutating goal shares: a dry run that prints the diff instead of
 * writing, an optional backup of the original file, and no-op detection so an already-satisfied
 * request leaves the file untouched.
 */
public abstract class AbstractPomWriteMojo extends AbstractPomMojo {

    /** Print the change as a unified diff and write nothing. */
    @Parameter(property = "organizer.dryRun", defaultValue = "false")
    boolean dryRun;

    /** Copy the POM to {@code <pom>.bak} before writing. */
    @Parameter(property = "organizer.backup", defaultValue = "false")
    boolean backup;

    /** Skip confirmation prompts for destructive changes. */
    @Parameter(property = "organizer.force", defaultValue = "false")
    boolean force;

    /** Suffix used for the backup file. */
    @Parameter(property = "organizer.backupSuffix", defaultValue = ".bak")
    String backupSuffix = ".bak";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        PomDocument pom = loadPom();
        run(pom);
        save(pom);
    }

    /**
     * Writes the POM, honouring {@code dryRun} and {@code backup}. Called automatically after
     * {@link #run(PomDocument)}; goals that write several POMs call it themselves per document.
     */
    protected void save(PomDocument pom) throws MojoExecutionException {
        if (!pom.isModified()) {
            getLog().info("No changes: " + describe(pom));
            return;
        }
        List<String> diff = PomDiff.of(pom);
        if (dryRun) {
            getLog().info("Dry run, not writing " + describe(pom) + ":");
            for (String line : diff) {
                getLog().info(line);
            }
            return;
        }
        try {
            if (backup) {
                Path source = pom.getPath();
                Path target = Paths.get(source.toString() + backupSuffix);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                getLog().info("Backed up " + source.getFileName() + " to " + target.getFileName());
            }
            pom.write();
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + describe(pom) + ": " + e.getMessage(), e);
        }
        getLog().info("Updated " + describe(pom) + " (" + countChangedLines(diff) + " lines changed)");
    }

    /**
     * Asks for confirmation before a destructive change. Returns true when the change should go
     * ahead: always so in batch mode, where {@code -Dorganizer.force} is implied by the fact that
     * the user spelled the request out on the command line.
     */
    protected boolean confirm(String question) {
        if (force || dryRun || !prompter().isInteractive()) {
            return true;
        }
        return prompter().confirm(question, false);
    }

    private static long countChangedLines(List<String> diff) {
        return diff.stream()
                .filter(line -> (line.startsWith("+") || line.startsWith("-"))
                        && !line.startsWith("+++")
                        && !line.startsWith("---"))
                .count();
    }

    private static String describe(PomDocument pom) {
        return pom.getPath() == null ? "pom.xml" : pom.getPath().toString();
    }
}
