package io.github.pgatzka.organizer.mojo;

import io.github.pgatzka.organizer.core.Prompter;
import io.github.pgatzka.organizer.support.Fixtures;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

/** Common wiring for the goal tests: a POM in a temp directory and a scripted prompter. */
abstract class MojoTest {

    @TempDir
    Path workspace;

    /** Writes a fixture as {@code pom.xml} in the workspace. */
    Path pom(String fixture) {
        return Fixtures.copyTo(workspace, fixture);
    }

    /** Writes POM text as {@code pom.xml} in the workspace. */
    Path pomText(String xml) {
        return Fixtures.write(workspace.resolve("pom.xml"), xml);
    }

    /** The current content of the workspace POM. */
    String content() {
        return Fixtures.read(workspace.resolve("pom.xml"));
    }

    /** Points a goal at the workspace POM and gives it a prompter that refuses to prompt. */
    <T extends AbstractPomMojo> T configure(T mojo) {
        return configure(mojo, ScriptedPrompter.batchMode());
    }

    /** Points a goal at the workspace POM with a scripted prompter. */
    <T extends AbstractPomMojo> T configure(T mojo, Prompter prompter) {
        mojo.pomFile = workspace.resolve("pom.xml").toFile();
        mojo.setPrompter(prompter);
        return mojo;
    }
}
