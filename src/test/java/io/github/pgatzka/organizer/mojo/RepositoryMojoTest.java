package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class RepositoryMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private static final String WITH_REPOSITORY = "<project>\n"
            + "  <repositories>\n"
            + "    <repository>\n"
            + "      <id>internal</id>\n"
            + "      <url>https://old.example.com/maven2</url>\n"
            + "    </repository>\n"
            + "  </repositories>\n"
            + "</project>";

    // ---------------------------------------------------------------- add

    @Test
    void addsARepository() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "internal";
        mojo.url = "https://repo.example.com/maven2";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <repositories>\n"
                        + "    <repository>\n"
                        + "      <id>internal</id>\n"
                        + "      <url>https://repo.example.com/maven2</url>\n"
                        + "    </repository>\n"
                        + "  </repositories>\n"
                        + "</project>");
    }

    @Test
    void writesTheNameAndLayout() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "internal";
        mojo.url = "https://repo.example.com/maven2";
        mojo.name = "Internal mirror";
        mojo.layout = "legacy";

        mojo.execute();

        assertThat(content())
                .contains("      <id>internal</id>\n"
                        + "      <name>Internal mirror</name>\n"
                        + "      <url>https://repo.example.com/maven2</url>\n"
                        + "      <layout>legacy</layout>");
    }

    @Test
    void writesTheReleaseAndSnapshotPolicies() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "snapshots";
        mojo.url = "https://repo.example.com/snapshots";
        mojo.releases = false;
        mojo.snapshots = true;

        mojo.execute();

        assertThat(content())
                .contains("      <releases>\n"
                        + "        <enabled>false</enabled>\n"
                        + "      </releases>\n"
                        + "      <snapshots>\n"
                        + "        <enabled>true</enabled>\n"
                        + "      </snapshots>");
    }

    @Test
    void writesIntoPluginRepositoriesWhenAsked() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "internal";
        mojo.url = "https://repo.example.com/maven2";
        mojo.pluginRepository = true;

        mojo.execute();

        assertThat(content())
                .contains("  <pluginRepositories>\n"
                        + "    <pluginRepository>\n"
                        + "      <id>internal</id>");
    }

    @Test
    void updatesAnExistingIdInPlace() throws Exception {
        pomText(WITH_REPOSITORY);
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.setLog(log);
        mojo.id = "internal";
        mojo.url = "https://new.example.com/maven2";

        mojo.execute();

        assertThat(content()).contains("https://new.example.com/maven2").doesNotContain("old.example.com");
        assertThat(content().split("<repository>", -1)).hasSize(2);
        assertThat(log.text()).contains("Updated repository internal");
    }

    @Test
    void reportsNoChangeWhenTheRepositoryIsAlreadyAsRequested() throws Exception {
        pomText(WITH_REPOSITORY);
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.setLog(log);
        mojo.id = "internal";
        mojo.url = "https://old.example.com/maven2";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No changes");
    }

    @Test
    void asksForTheIdAndUrl() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ScriptedPrompter prompter = new ScriptedPrompter("internal", "https://repo.example.com/maven2");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions()).containsExactly("Repository id", "Repository URL");
        assertThat(content()).contains("<id>internal</id>");
    }

    @Test
    void offersTheCurrentUrlAsTheDefault() throws Exception {
        pomText(WITH_REPOSITORY);
        ScriptedPrompter prompter = new ScriptedPrompter("");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo(), prompter);
        mojo.id = "internal";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void failsInBatchModeWithoutAnId() {
        pomText(WITH_REPOSITORY);
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Did=<value>");
    }

    @Test
    void failsInBatchModeWithoutAUrl() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddRepositoryMojo mojo = configure(new AddRepositoryMojo());
        mojo.id = "internal";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Durl=<value>");
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removesARepositoryAndPrunesTheSection() throws Exception {
        pomText(WITH_REPOSITORY);
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.id = "internal";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void keepsTheSectionWhenAsked() throws Exception {
        pomText(WITH_REPOSITORY);
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.id = "internal";
        mojo.force = true;
        mojo.removeEmptyElements = false;

        mojo.execute();

        assertThat(content()).contains("<repositories>").doesNotContain("<repository>");
    }

    @Test
    void leavesOtherRepositoriesAlone() throws Exception {
        pomText("<project>\n  <repositories>\n"
                + "    <repository>\n      <id>one</id>\n      <url>https://one.example.com</url>\n    </repository>\n"
                + "    <repository>\n      <id>two</id>\n      <url>https://two.example.com</url>\n    </repository>\n"
                + "  </repositories>\n</project>");
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.id = "one";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).contains("<id>two</id>").doesNotContain("<id>one</id>");
    }

    @Test
    void pluginRepositoriesAreASeparateSection() throws Exception {
        pomText("<project>\n"
                + "  <repositories>\n    <repository>\n      <id>shared</id>\n"
                + "      <url>https://a.example.com</url>\n    </repository>\n  </repositories>\n"
                + "  <pluginRepositories>\n    <pluginRepository>\n      <id>shared</id>\n"
                + "      <url>https://b.example.com</url>\n    </pluginRepository>\n  </pluginRepositories>\n"
                + "</project>");
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.id = "shared";
        mojo.pluginRepository = true;
        mojo.force = true;

        mojo.execute();

        assertThat(content()).doesNotContain("pluginRepositories").contains("https://a.example.com");
    }

    @Test
    void reportsAMissingRepositoryWithoutFailing() throws Exception {
        pomText(WITH_REPOSITORY);
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.setLog(log);
        mojo.id = "nope";
        mojo.force = true;
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
        assertThat(log.text()).contains("No repository with id nope");
    }

    @Test
    void failsOnAMissingRepositoryWhenAsked() {
        pomText(WITH_REPOSITORY);
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.id = "nope";
        mojo.failIfMissing = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void handlesAPomWithoutRepositories() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo());
        mojo.setLog(log);
        mojo.id = "internal";

        mojo.execute();

        assertThat(log.text()).contains("declares no repositories");
    }

    @Test
    void offersTheRepositoriesToChooseFrom() throws Exception {
        pomText(WITH_REPOSITORY);
        ScriptedPrompter prompter = new ScriptedPrompter("1", "y");
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0))
                .contains("Which repository should be removed?")
                .contains("internal  https://old.example.com/maven2");
        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void asksBeforeRemoving() throws Exception {
        pomText(WITH_REPOSITORY);
        ScriptedPrompter prompter = new ScriptedPrompter("n");
        RemoveRepositoryMojo mojo = configure(new RemoveRepositoryMojo(), prompter);
        mojo.id = "internal";
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }
}
