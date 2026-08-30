package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class ListDependenciesMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private ListDependenciesMojo mojo() {
        pom("sample.xml");
        ListDependenciesMojo mojo = configure(new ListDependenciesMojo());
        mojo.setLog(log);
        mojo.format = "plain";
        return mojo;
    }

    @Test
    void listsEveryDeclaredDependency() throws Exception {
        mojo().execute();

        assertThat(log.messages())
                .containsExactly(
                        "org.apache.commons:commons-lang3:3.14.0 [compile]",
                        "org.junit.jupiter:junit-jupiter:5.11.4 [test]");
    }

    @Test
    void neverWritesTheFile() throws Exception {
        ListDependenciesMojo mojo = mojo();
        String before = content();

        mojo.execute();

        assertThat(content()).isEqualTo(before);
    }

    @Test
    void filtersByScope() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.scope = "test";

        mojo.execute();

        assertThat(log.messages()).containsExactly("org.junit.jupiter:junit-jupiter:5.11.4 [test]");
    }

    @Test
    void filtersByCoordinatePattern() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.filter = "org.apache.*:*";

        mojo.execute();

        assertThat(log.messages()).containsExactly("org.apache.commons:commons-lang3:3.14.0 [compile]");
    }

    @Test
    void combinesFilters() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.filter = "org.apache.*:*";
        mojo.scope = "test";

        mojo.execute();

        assertThat(log.messages()).containsExactly("No dependencies match.");
    }

    @Test
    void saysSoWhenNothingMatches() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.filter = "nothing:here";

        mojo.execute();

        assertThat(log.messages()).containsExactly("No dependencies match.");
    }

    @Test
    void handlesAPomWithoutDependencies() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ListDependenciesMojo mojo = configure(new ListDependenciesMojo());
        mojo.setLog(log);

        mojo.execute();

        assertThat(log.messages()).containsExactly("No dependencies match.");
    }

    @Test
    void marksManagedEntries() throws Exception {
        pomText("<project>\n"
                + "  <dependencyManagement>\n"
                + "    <dependencies>\n"
                + "      <dependency>\n"
                + "        <groupId>com.acme</groupId>\n"
                + "        <artifactId>widget</artifactId>\n"
                + "        <version>9.9.9</version>\n"
                + "      </dependency>\n"
                + "    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n"
                + "      <artifactId>widget</artifactId>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>");
        ListDependenciesMojo mojo = configure(new ListDependenciesMojo());
        mojo.setLog(log);
        mojo.includeManaged = true;

        mojo.execute();

        assertThat(log.messages())
                .containsExactly(
                        "com.acme:widget:(managed) [compile]",
                        "com.acme:widget:9.9.9 [compile] (dependencyManagement)");
    }

    @Test
    void leavesOutManagedEntriesByDefault() throws Exception {
        ListDependenciesMojo mojo = mojo();

        mojo.execute();

        assertThat(log.text()).doesNotContain("dependencyManagement");
    }

    @Test
    void rendersATable() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.format = "table";

        mojo.execute();

        assertThat(log.messages().get(0)).startsWith("GROUP ID").contains("ARTIFACT ID").contains("SCOPE");
        assertThat(log.messages().get(2)).startsWith("org.apache.commons  commons-lang3");
        assertThat(log.messages().get(3)).startsWith("org.junit.jupiter   junit-jupiter");
    }

    @Test
    void rendersATree() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.format = "tree";

        mojo.execute();

        assertThat(log.messages())
                .containsExactly(
                        "compile",
                        "\\- org.apache.commons:commons-lang3:3.14.0",
                        "test",
                        "\\- org.junit.jupiter:junit-jupiter:5.11.4");
    }

    @Test
    void acceptsTheFormatNameInAnyCase() throws Exception {
        ListDependenciesMojo mojo = mojo();
        mojo.format = "TrEe";

        mojo.execute();

        assertThat(log.messages()).contains("compile");
    }

    @Test
    void rejectsAnUnknownFormat() {
        ListDependenciesMojo mojo = mojo();
        mojo.format = "yaml";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Choose one of plain, table or tree");
    }

    @Test
    void rejectsAMalformedFilter() {
        ListDependenciesMojo mojo = mojo();
        mojo.filter = "not-a-coordinate";

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
    }
}
