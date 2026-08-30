package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pgatzka.organizer.support.Fixtures;
import io.github.pgatzka.organizer.support.RecordingLog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The cross-cutting safety net every mutating goal inherits. */
class DryRunAndBackupTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private AddDependencyMojo mojo() {
        pom("sample.xml");
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.setLog(log);
        mojo.artifact = "com.acme:widget:1.0.0";
        return mojo;
    }

    @Test
    void aDryRunLeavesTheFileUntouched() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.dryRun = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void aDryRunPrintsAUnifiedDiff() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.dryRun = true;

        mojo.execute();

        assertThat(log.text())
                .contains("Dry run, not writing")
                .contains("+++ ")
                .contains("+      <artifactId>widget</artifactId>");
    }

    @Test
    void aDryRunDiffOnlyCoversTheChange() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.dryRun = true;

        mojo.execute();

        assertThat(log.messages().stream().filter(line -> line.startsWith("+") && !line.startsWith("+++")))
                .hasSize(5);
    }

    @Test
    void backupKeepsThePreChangeContent() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.backup = true;

        mojo.execute();

        Path backup = workspace.resolve("pom.xml.bak");
        assertThat(backup).exists();
        assertThat(Fixtures.read(backup)).isEqualTo(Fixtures.text("sample.xml"));
        assertThat(content()).contains("widget");
    }

    @Test
    void backupUsesTheConfiguredSuffix() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.backup = true;
        mojo.backupSuffix = ".orig";

        mojo.execute();

        assertThat(workspace.resolve("pom.xml.orig")).exists();
    }

    @Test
    void backupOverwritesAnEarlierBackup() throws Exception {
        Fixtures.write(workspace.resolve("pom.xml.bak"), "stale");
        AddDependencyMojo mojo = mojo();
        mojo.backup = true;

        mojo.execute();

        assertThat(Fixtures.read(workspace.resolve("pom.xml.bak"))).isNotEqualTo("stale");
    }

    @Test
    void noBackupIsWrittenWithoutTheFlag() throws Exception {
        mojo().execute();

        assertThat(workspace.resolve("pom.xml.bak")).doesNotExist();
    }

    @Test
    void aDryRunWritesNoBackupEither() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.dryRun = true;
        mojo.backup = true;

        mojo.execute();

        assertThat(workspace.resolve("pom.xml.bak")).doesNotExist();
    }

    @Test
    void anUnchangedPomIsNotRewritten() throws Exception {
        AddDependencyMojo mojo = mojo();
        mojo.artifact = "org.apache.commons:commons-lang3:3.14.0";
        long before = Files.getLastModifiedTime(workspace.resolve("pom.xml")).toMillis();

        mojo.execute();

        assertThat(log.text()).contains("No changes");
        assertThat(Files.getLastModifiedTime(workspace.resolve("pom.xml")).toMillis()).isEqualTo(before);
    }

    @Test
    void aWriteReportsHowManyLinesChanged() throws Exception {
        mojo().execute();

        assertThat(log.text()).contains("lines changed");
    }

    @Test
    void runningTheSameGoalTwiceIsIdempotent() throws Exception {
        mojo().execute();
        String afterFirst = content();

        AddDependencyMojo second = configure(new AddDependencyMojo());
        second.setLog(log);
        second.artifact = "com.acme:widget:1.0.0";
        second.execute();

        assertThat(content()).isEqualTo(afterFirst);
    }
}
