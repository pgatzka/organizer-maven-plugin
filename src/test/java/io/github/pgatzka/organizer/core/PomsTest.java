package io.github.pgatzka.organizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pgatzka.organizer.support.Fixtures;
import java.util.Comparator;
import org.jdom2.Element;
import org.junit.jupiter.api.Test;

class PomsTest {

    private static PomDocument sample() {
        return PomDocument.parse(Fixtures.text("sample.xml"), null);
    }

    private static PomDocument parse(String xml) {
        return PomDocument.parse(xml, null);
    }

    // ---------------------------------------------------------------- navigation

    @Test
    void findsChildrenInsideTheDocumentNamespace() {
        PomDocument pom = sample();

        assertThat(Poms.childText(pom.getRoot(), "artifactId")).isEqualTo("demo");
    }

    @Test
    void findsChildrenInANamespacelessPom() {
        PomDocument pom = parse(Fixtures.text("no-namespace.xml"));

        assertThat(Poms.childText(pom.getRoot(), "artifactId")).isEqualTo("tabs");
    }

    @Test
    void findsChildrenWrittenWithoutANamespaceInsideANamespacedPom() {
        PomDocument pom = parse("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <name xmlns=\"\">mixed</name>\n"
                + "</project>");

        assertThat(Poms.childText(pom.getRoot(), "name")).isEqualTo("mixed");
    }

    @Test
    void returnsNullForAMissingChild() {
        assertThat(Poms.child(sample().getRoot(), "nope")).isNull();
        assertThat(Poms.child(null, "anything")).isNull();
        assertThat(Poms.childText(sample().getRoot(), "nope")).isNull();
    }

    @Test
    void fallsBackWhenAChildIsMissingOrBlank() {
        assertThat(Poms.childText(sample().getRoot(), "nope", "fallback")).isEqualTo("fallback");
        assertThat(Poms.childText(sample().getRoot(), "artifactId", "fallback")).isEqualTo("demo");
    }

    @Test
    void walksAPathOfChildNames() {
        PomDocument pom = sample();

        assertThat(Poms.path(pom.getRoot(), "build", "plugins")).isPresent();
        assertThat(Poms.path(pom.getRoot(), "build", "nope", "plugins")).isEmpty();
    }

    @Test
    void listsRepeatedChildren() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        assertThat(Poms.children(dependencies, "dependency")).hasSize(2);
        assertThat(Poms.children(null, "dependency")).isEmpty();
    }

    // ---------------------------------------------------------------- appending

    @Test
    void appendsToAnExistingSectionWithMatchingIndentation() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Element dependency = Poms.element(pom.getRoot(), "dependency");
        dependency.addContent(Poms.element(pom.getRoot(), "groupId", "com.acme"));
        dependency.addContent(Poms.element(pom.getRoot(), "artifactId", "widget"));
        Poms.append(dependencies, dependency, pom.getIndentUnit());

        assertThat(pom.render())
                .contains("      <scope>test</scope>\n"
                        + "    </dependency>\n"
                        + "    <dependency>\n"
                        + "      <groupId>com.acme</groupId>\n"
                        + "      <artifactId>widget</artifactId>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>");
    }

    @Test
    void appendsIntoASelfClosingSection() {
        PomDocument pom = parse("<project>\n  <dependencies/>\n</project>");
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.append(dependencies, Poms.element(pom.getRoot(), "dependency", "x"), pom.getIndentUnit());

        assertThat(pom.render())
                .isEqualTo("<project>\n"
                        + "  <dependencies>\n"
                        + "    <dependency>x</dependency>\n"
                        + "  </dependencies>\n"
                        + "</project>");
    }

    @Test
    void appendsIntoAnEmptyButOpenSection() {
        PomDocument pom = parse("<project>\n  <dependencies>\n  </dependencies>\n</project>");
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.append(dependencies, Poms.element(pom.getRoot(), "dependency", "x"), pom.getIndentUnit());

        assertThat(pom.render()).contains("  <dependencies>\n    <dependency>x</dependency>\n  </dependencies>");
    }

    @Test
    void appendsIntoASectionWhoseClosingTagSharesTheLine() {
        PomDocument pom = parse("<project>\n  <dependencies>  </dependencies>\n</project>");
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.append(dependencies, Poms.element(pom.getRoot(), "dependency", "x"), pom.getIndentUnit());

        assertThat(pom.render()).contains("  <dependencies>\n    <dependency>x</dependency>\n  </dependencies>");
    }

    @Test
    void indentsNestedSubtreesOnAppend() {
        PomDocument pom = parse("<project>\n  <build/>\n</project>");
        Element build = Poms.child(pom.getRoot(), "build");

        Element plugins = Poms.element(pom.getRoot(), "plugins");
        Element plugin = Poms.element(pom.getRoot(), "plugin");
        plugin.addContent(Poms.element(pom.getRoot(), "artifactId", "a-plugin"));
        plugins.addContent(plugin);
        Poms.append(build, plugins, pom.getIndentUnit());

        assertThat(pom.render())
                .isEqualTo("<project>\n"
                        + "  <build>\n"
                        + "    <plugins>\n"
                        + "      <plugin>\n"
                        + "        <artifactId>a-plugin</artifactId>\n"
                        + "      </plugin>\n"
                        + "    </plugins>\n"
                        + "  </build>\n"
                        + "</project>");
    }

    @Test
    void appendsUsingTabsWhenTheDocumentUsesTabs() {
        PomDocument pom = parse(Fixtures.text("no-namespace.xml"));
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.append(dependencies, Poms.element(pom.getRoot(), "dependency", "x"), pom.getIndentUnit());

        assertThat(pom.render()).contains("\n\t\t<dependency>x</dependency>\n\t</dependencies>");
    }

    // ---------------------------------------------------------------- inserting

    @Test
    void insertsBeforeAReferenceElement() {
        PomDocument pom = parse("<project>\n  <modules>\n    <module>b</module>\n  </modules>\n</project>");
        Element modules = Poms.child(pom.getRoot(), "modules");
        Element reference = Poms.children(modules, "module").get(0);

        Poms.insertBefore(modules, Poms.element(pom.getRoot(), "module", "a"), reference, pom.getIndentUnit());

        assertThat(pom.render())
                .contains("  <modules>\n    <module>a</module>\n    <module>b</module>\n  </modules>");
    }

    @Test
    void rejectsAReferenceFromAnotherParent() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");
        Element stranger = Poms.child(pom.getRoot(), "properties");

        assertThatThrownBy(() -> Poms.insertBefore(
                        dependencies, Poms.element(pom.getRoot(), "dependency"), stranger, pom.getIndentUnit()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void insertsIntoASortedSectionInOrder() {
        PomDocument pom = parse(
                "<project>\n  <modules>\n    <module>alpha</module>\n    <module>gamma</module>\n  </modules>\n</project>");
        Element modules = Poms.child(pom.getRoot(), "modules");

        Poms.insertSorted(
                modules,
                Poms.element(pom.getRoot(), "module", "beta"),
                "module",
                Comparator.comparing(Element::getTextTrim),
                pom.getIndentUnit());

        assertThat(pom.render())
                .contains("<module>alpha</module>\n    <module>beta</module>\n    <module>gamma</module>");
    }

    @Test
    void appendsToASortedSectionWhenTheNewEntrySortsLast() {
        PomDocument pom = parse("<project>\n  <modules>\n    <module>alpha</module>\n  </modules>\n</project>");
        Element modules = Poms.child(pom.getRoot(), "modules");

        Poms.insertSorted(
                modules,
                Poms.element(pom.getRoot(), "module", "zeta"),
                "module",
                Comparator.comparing(Element::getTextTrim),
                pom.getIndentUnit());

        assertThat(pom.render()).contains("<module>alpha</module>\n    <module>zeta</module>");
    }

    @Test
    void doesNotReorderAHandSortedSection() {
        PomDocument pom = parse(
                "<project>\n  <modules>\n    <module>zeta</module>\n    <module>alpha</module>\n  </modules>\n</project>");
        Element modules = Poms.child(pom.getRoot(), "modules");

        Poms.insertSorted(
                modules,
                Poms.element(pom.getRoot(), "module", "beta"),
                "module",
                Comparator.comparing(Element::getTextTrim),
                pom.getIndentUnit());

        assertThat(pom.render())
                .contains("<module>zeta</module>\n    <module>alpha</module>\n    <module>beta</module>");
    }

    @Test
    void refusesToIndentInsideADetachedElement() {
        PomDocument pom = sample();
        Element detached = Poms.element(pom.getRoot(), "dependency");

        assertThatThrownBy(() -> Poms.append(detached, Poms.element(pom.getRoot(), "groupId", "g"), "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not part of a document yet");
    }

    @Test
    void refusesToInsertBeforeInsideADetachedElement() {
        PomDocument pom = sample();
        Element detached = Poms.element(pom.getRoot(), "dependency");
        Element reference = Poms.element(pom.getRoot(), "groupId", "g");
        detached.addContent(reference);

        assertThatThrownBy(() -> Poms.insertBefore(
                        detached, Poms.element(pom.getRoot(), "artifactId", "a"), reference, "  "))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- creating

    @Test
    void createsAMissingChildOnDemand() {
        PomDocument pom = parse("<project>\n  <artifactId>a</artifactId>\n</project>");

        Element created = Poms.childOrCreate(pom.getRoot(), "dependencies", pom.getIndentUnit());
        Poms.append(created, Poms.element(pom.getRoot(), "dependency", "x"), pom.getIndentUnit());

        assertThat(pom.render())
                .isEqualTo("<project>\n"
                        + "  <artifactId>a</artifactId>\n"
                        + "  <dependencies>\n"
                        + "    <dependency>x</dependency>\n"
                        + "  </dependencies>\n"
                        + "</project>");
    }

    @Test
    void returnsTheExistingChildInsteadOfCreatingASecondOne() {
        PomDocument pom = sample();

        Element first = Poms.childOrCreate(pom.getRoot(), "dependencies", pom.getIndentUnit());
        Element second = Poms.childOrCreate(pom.getRoot(), "dependencies", pom.getIndentUnit());

        assertThat(first).isSameAs(second);
        assertThat(pom.isModified()).isFalse();
    }

    @Test
    void createsEveryMissingLinkOfAPath() {
        PomDocument pom = parse("<project>\n  <artifactId>a</artifactId>\n</project>");

        Element plugins = Poms.pathOrCreate(pom.getRoot(), pom.getIndentUnit(), "build", "pluginManagement", "plugins");
        Poms.append(plugins, Poms.element(pom.getRoot(), "plugin", "p"), pom.getIndentUnit());

        assertThat(pom.render())
                .contains("  <build>\n"
                        + "    <pluginManagement>\n"
                        + "      <plugins>\n"
                        + "        <plugin>p</plugin>\n"
                        + "      </plugins>\n"
                        + "    </pluginManagement>\n"
                        + "  </build>");
    }

    @Test
    void setsChildTextCreatingTheChildWhenMissing() {
        PomDocument pom = parse("<project>\n  <artifactId>a</artifactId>\n</project>");

        Poms.setChildText(pom.getRoot(), "version", "1.0.0", pom.getIndentUnit());
        Poms.setChildText(pom.getRoot(), "artifactId", "b", pom.getIndentUnit());

        assertThat(pom.render())
                .isEqualTo("<project>\n  <artifactId>b</artifactId>\n  <version>1.0.0</version>\n</project>");
    }

    // ---------------------------------------------------------------- removing

    @Test
    void removesAnElementAndItsIndentation() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");
        Element first = Poms.children(dependencies, "dependency").get(0);

        Poms.remove(dependencies, first);

        assertThat(pom.render())
                .contains("  <dependencies>\n    <!-- test scope below -->\n    <dependency>")
                .doesNotContain("commons-lang3");
    }

    @Test
    void leavesNeighbouringCommentsInPlaceOnRemoval() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");
        Element second = Poms.children(dependencies, "dependency").get(1);

        Poms.remove(dependencies, second);

        assertThat(pom.render()).contains("<!-- test scope below -->");
    }

    @Test
    void removingAStrangerIsANoOp() {
        PomDocument pom = sample();
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.remove(dependencies, Poms.element(pom.getRoot(), "dependency"));

        assertThat(pom.isModified()).isFalse();
    }

    @Test
    void removesANamedChild() {
        PomDocument pom = sample();

        assertThat(Poms.removeChild(pom.getRoot(), "properties")).isTrue();
        assertThat(Poms.removeChild(pom.getRoot(), "properties")).isFalse();
        assertThat(pom.render()).doesNotContain("<properties>");
    }

    @Test
    void prunesContainersLeftEmptyByARemoval() {
        PomDocument pom = parse("<project>\n"
                + "  <build>\n    <plugins>\n      <plugin>p</plugin>\n    </plugins>\n  </build>\n"
                + "</project>");
        Element plugin = Poms.path(pom.getRoot(), "build", "plugins", "plugin").orElseThrow();

        Poms.removeAndPrune(plugin);

        assertThat(pom.render()).isEqualTo("<project>\n</project>");
    }

    @Test
    void stopsPruningAtTheDocumentElement() {
        PomDocument pom = parse("<project>\n  <name>x</name>\n</project>");

        Poms.removeAndPrune(Poms.child(pom.getRoot(), "name"));

        assertThat(pom.render()).isEqualTo("<project>\n</project>");
    }

    @Test
    void doesNotPruneAContainerThatStillHasChildren() {
        PomDocument pom = parse("<project>\n"
                + "  <dependencies>\n    <dependency>a</dependency>\n    <dependency>b</dependency>\n"
                + "  </dependencies>\n</project>");
        Element dependencies = Poms.child(pom.getRoot(), "dependencies");

        Poms.removeAndPrune(Poms.children(dependencies, "dependency").get(0));

        assertThat(pom.render()).contains("<dependencies>\n    <dependency>b</dependency>\n  </dependencies>");
    }

    @Test
    void pruningADetachedElementIsANoOp() {
        PomDocument pom = sample();

        Poms.removeAndPrune(Poms.element(pom.getRoot(), "orphan"));

        assertThat(pom.isModified()).isFalse();
    }

    // ---------------------------------------------------------------- misc

    @Test
    void reportsDepthRelativeToTheDocumentElement() {
        PomDocument pom = sample();

        assertThat(Poms.depthOf(pom.getRoot())).isZero();
        assertThat(Poms.depthOf(Poms.child(pom.getRoot(), "dependencies"))).isEqualTo(1);
        assertThat(Poms.depthOf(Poms.path(pom.getRoot(), "build", "plugins").orElseThrow())).isEqualTo(2);
    }

    @Test
    void reportsWhetherAnElementHasElementChildren() {
        PomDocument pom = sample();

        assertThat(Poms.hasElementChildren(Poms.child(pom.getRoot(), "dependencies"))).isTrue();
        assertThat(Poms.hasElementChildren(Poms.child(pom.getRoot(), "artifactId"))).isFalse();
    }
}
