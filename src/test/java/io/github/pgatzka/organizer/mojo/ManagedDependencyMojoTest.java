package io.github.pgatzka.organizer.mojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.RecordingLog;
import io.github.pgatzka.organizer.support.ScriptedPrompter;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

/** The dependencyManagement and BOM goals. */
class ManagedDependencyMojoTest extends MojoTest {

    private final RecordingLog log = new RecordingLog();

    private static final String WITH_MANAGEMENT = "<project>\n"
            + "  <dependencyManagement>\n"
            + "    <dependencies>\n"
            + "      <dependency>\n"
            + "        <groupId>com.acme</groupId>\n"
            + "        <artifactId>widget</artifactId>\n"
            + "        <version>1.0.0</version>\n"
            + "      </dependency>\n"
            + "    </dependencies>\n"
            + "  </dependencyManagement>\n"
            + "</project>";

    @Test
    void createsTheManagementSectionWhenMissing() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        AddManagedDependencyMojo mojo = configure(new AddManagedDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(content())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <dependencyManagement>\n"
                        + "    <dependencies>\n"
                        + "      <dependency>\n"
                        + "        <groupId>com.acme</groupId>\n"
                        + "        <artifactId>widget</artifactId>\n"
                        + "        <version>1.0.0</version>\n"
                        + "      </dependency>\n"
                        + "    </dependencies>\n"
                        + "  </dependencyManagement>\n"
                        + "</project>");
    }

    @Test
    void doesNotTouchThePlainDependenciesSection() throws Exception {
        pom("sample.xml");
        AddManagedDependencyMojo mojo = configure(new AddManagedDependencyMojo());
        mojo.artifact = "com.acme:widget:1.0.0";

        mojo.execute();

        assertThat(content())
                .contains("<dependencyManagement>")
                .contains("  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>org.apache.commons</groupId>");
    }

    @Test
    void updatesAManagedVersionInPlace() throws Exception {
        pomText(WITH_MANAGEMENT);
        AddManagedDependencyMojo mojo = configure(new AddManagedDependencyMojo());
        mojo.setLog(log);
        mojo.artifact = "com.acme:widget:2.0.0";

        mojo.execute();

        assertThat(content()).contains("<version>2.0.0</version>").doesNotContain("1.0.0");
        assertThat(log.text()).contains("Updated managed dependency");
    }

    @Test
    void failsOnAnExistingManagedEntryWhenAsked() {
        pomText(WITH_MANAGEMENT);
        AddManagedDependencyMojo mojo = configure(new AddManagedDependencyMojo());
        mojo.artifact = "com.acme:widget:2.0.0";
        mojo.failOnExisting = true;

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("already declared in dependencyManagement");
    }

    @Test
    void addingADependencyManagedByThisPomOmitsTheVersion() throws Exception {
        pomText(WITH_MANAGEMENT);
        AddDependencyMojo mojo = configure(new AddDependencyMojo());
        mojo.artifact = "com.acme:widget";

        mojo.execute();

        assertThat(content())
                .contains("  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>com.acme</groupId>\n"
                        + "      <artifactId>widget</artifactId>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>");
    }

    @Test
    void removesAManagedEntry() throws Exception {
        pomText(WITH_MANAGEMENT);
        RemoveManagedDependencyMojo mojo = configure(new RemoveManagedDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.force = true;

        mojo.execute();

        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    @Test
    void removingAManagedEntryLeavesTheDependencyAlone() throws Exception {
        pomText("<project>\n"
                + "  <dependencyManagement>\n    <dependencies>\n      <dependency>\n"
                + "        <groupId>com.acme</groupId>\n        <artifactId>widget</artifactId>\n"
                + "        <version>1.0.0</version>\n      </dependency>\n    </dependencies>\n"
                + "  </dependencyManagement>\n"
                + "  <dependencies>\n    <dependency>\n"
                + "      <groupId>com.acme</groupId>\n      <artifactId>widget</artifactId>\n"
                + "    </dependency>\n  </dependencies>\n"
                + "</project>");
        RemoveManagedDependencyMojo mojo = configure(new RemoveManagedDependencyMojo());
        mojo.artifact = "com.acme:widget";
        mojo.force = true;

        mojo.execute();

        assertThat(content())
                .doesNotContain("dependencyManagement")
                .contains("  <dependencies>\n    <dependency>");
    }

    @Test
    void reportsAPomWithNoManagementSection() throws Exception {
        pom("sample.xml");
        RemoveManagedDependencyMojo mojo = configure(new RemoveManagedDependencyMojo());
        mojo.setLog(log);
        mojo.artifact = "com.acme:widget";
        String before = content();

        mojo.execute();

        assertThat(log.text()).contains("declares no managed dependency entries");
        assertThat(content()).isEqualTo(before);
    }

    @Test
    void offersTheManagedEntriesToChooseFrom() throws Exception {
        pomText(WITH_MANAGEMENT);
        ScriptedPrompter prompter = new ScriptedPrompter("1", "y");
        RemoveManagedDependencyMojo mojo = configure(new RemoveManagedDependencyMojo(), prompter);

        mojo.execute();

        assertThat(prompter.questions().get(0)).contains("Which managed dependency should be removed?");
        assertThat(content()).isEqualTo("<project>\n</project>");
    }

    // ---------------------------------------------------------------- BOM import

    @Test
    void importsABom() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo mojo = configure(new ImportBomMojo());
        mojo.bom = "org.springframework.boot:spring-boot-dependencies:3.2.0";

        mojo.execute();

        assertThat(content())
                .contains("      <dependency>\n"
                        + "        <groupId>org.springframework.boot</groupId>\n"
                        + "        <artifactId>spring-boot-dependencies</artifactId>\n"
                        + "        <version>3.2.0</version>\n"
                        + "        <type>pom</type>\n"
                        + "        <scope>import</scope>\n"
                        + "      </dependency>");
    }

    @Test
    void reimportingUpdatesTheVersionInPlace() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo first = configure(new ImportBomMojo());
        first.bom = "org.springframework.boot:spring-boot-dependencies:3.2.0";
        first.execute();

        ImportBomMojo second = configure(new ImportBomMojo());
        second.setLog(log);
        second.bom = "org.springframework.boot:spring-boot-dependencies:3.3.0";
        second.execute();

        assertThat(content()).contains("<version>3.3.0</version>").doesNotContain("3.2.0");
        assertThat(content().split("spring-boot-dependencies", -1)).hasSize(2);
        assertThat(log.text()).contains("Updated imported BOM");
    }

    @Test
    void reportsNoChangeWhenTheBomIsAlreadyImported() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo first = configure(new ImportBomMojo());
        first.bom = "org.springframework.boot:spring-boot-dependencies:3.2.0";
        first.execute();
        String after = content();

        ImportBomMojo second = configure(new ImportBomMojo());
        second.setLog(log);
        second.bom = "org.springframework.boot:spring-boot-dependencies:3.2.0";
        second.execute();

        assertThat(content()).isEqualTo(after);
        assertThat(log.text()).contains("is already imported");
    }

    @Test
    void aBomIsAlwaysTypePomEvenIfSomethingElseWasAsked() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo mojo = configure(new ImportBomMojo());
        mojo.bom = "g:a:1.0";
        mojo.type = "jar";

        mojo.execute();

        assertThat(content()).contains("<type>pom</type>").doesNotContain("<type>jar</type>");
    }

    @Test
    void theBomCanAlsoBeGivenAsArtifact() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo mojo = configure(new ImportBomMojo());
        mojo.artifact = "g:a:1.0";

        mojo.execute();

        assertThat(content()).contains("<scope>import</scope>");
    }

    @Test
    void aBomNeedsAVersion() {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo mojo = configure(new ImportBomMojo());
        mojo.bom = "g:a";

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No version given for g:a");
    }

    @Test
    void anImportedBomCanBeRemovedAsAManagedEntry() throws Exception {
        pomText("<project>\n  <artifactId>a</artifactId>\n</project>");
        ImportBomMojo importer = configure(new ImportBomMojo());
        importer.bom = "g:a:1.0";
        importer.execute();

        RemoveManagedDependencyMojo remover = configure(new RemoveManagedDependencyMojo());
        remover.artifact = "g:a";
        remover.force = true;
        remover.execute();

        assertThat(content()).isEqualTo("<project>\n  <artifactId>a</artifactId>\n</project>");
    }
}
