package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jdom2.Element;

/** Reading and writing {@code <dependency>} entries. */
public final class Dependencies {

    /** The order the POM schema gives to the children of {@code <dependency>}. */
    public static final List<String> ELEMENT_ORDER = List.of(
            "groupId", "artifactId", "version", "type", "classifier", "scope", "systemPath", "optional", "exclusions");

    /** Sorts dependencies the way most projects group them: by scope, then by coordinate. */
    public static final Comparator<Element> BY_COORDINATE = Comparator
            .comparing((Element e) -> Poms.childText(e, "groupId", ""))
            .thenComparing(e -> Poms.childText(e, "artifactId", ""));

    private Dependencies() {}

    /** The coordinate declared by a {@code <dependency>} element. */
    public static Coordinate coordinateOf(Element dependency) {
        return new Coordinate(
                Poms.childText(dependency, "groupId", "unknown"),
                Poms.childText(dependency, "artifactId", "unknown"),
                Poms.childText(dependency, "version"),
                Poms.childText(dependency, "classifier"),
                Poms.childText(dependency, "type"));
    }

    /**
     * Whether {@code dependency} is the one {@code wanted} refers to.
     *
     * <p>Group and artifact must match, with {@code *} accepted as a wildcard. A classifier or type
     * given in {@code wanted} must match too, so {@code g:a:1::test-jar} does not match the plain
     * jar; leaving them out matches either way.
     */
    public static boolean matches(Element dependency, Coordinate wanted) {
        if (!wanted.matchesGA(Poms.childText(dependency, "groupId"), Poms.childText(dependency, "artifactId"))) {
            return false;
        }
        if (wanted.getClassifier() != null
                && !wanted.getClassifier().equals(Poms.childText(dependency, "classifier"))) {
            return false;
        }
        return wanted.getType() == null || wanted.getType().equals(Poms.childText(dependency, "type"));
    }

    /** Every dependency in {@code container} matching {@code wanted}. */
    public static List<Element> findAll(Element container, Coordinate wanted) {
        List<Element> found = new ArrayList<>();
        for (Element dependency : Poms.children(container, "dependency")) {
            if (matches(dependency, wanted)) {
                found.add(dependency);
            }
        }
        return found;
    }

    /** The first dependency in {@code container} matching {@code wanted}. */
    public static Optional<Element> find(Element container, Coordinate wanted) {
        return findAll(container, wanted).stream().findFirst();
    }

    /**
     * Looks up the version {@code <dependencyManagement>} declares for {@code wanted}, if any.
     *
     * @param managementContainer the {@code <dependencies>} element inside
     *     {@code <dependencyManagement>}, or {@code null}
     */
    public static Optional<String> managedVersion(Element managementContainer, Coordinate wanted) {
        if (managementContainer == null) {
            return Optional.empty();
        }
        return find(managementContainer, wanted).map(entry -> Poms.childText(entry, "version"));
    }

    /** Builds a detached {@code <dependency>} element. */
    public static Element build(Element context, Coordinate coordinate, DependencyOptions options) {
        Element dependency = Poms.element(context, "dependency");
        dependency.addContent(Poms.element(context, "groupId", coordinate.getGroupId()));
        dependency.addContent(Poms.element(context, "artifactId", coordinate.getArtifactId()));
        if (coordinate.getVersion() != null) {
            dependency.addContent(Poms.element(context, "version", coordinate.getVersion()));
        }
        if (coordinate.getType() != null) {
            dependency.addContent(Poms.element(context, "type", coordinate.getType()));
        }
        if (coordinate.getClassifier() != null) {
            dependency.addContent(Poms.element(context, "classifier", coordinate.getClassifier()));
        }
        if (options.scope() != null) {
            dependency.addContent(Poms.element(context, "scope", options.scope()));
        }
        if (Boolean.TRUE.equals(options.optional())) {
            dependency.addContent(Poms.element(context, "optional", "true"));
        }
        if (!options.exclusions().isEmpty()) {
            dependency.addContent(buildExclusions(context, options.exclusions()));
        }
        return dependency;
    }

    private static Element buildExclusions(Element context, List<Coordinate> exclusions) {
        Element container = Poms.element(context, "exclusions");
        for (Coordinate excluded : exclusions) {
            Element exclusion = Poms.element(context, "exclusion");
            exclusion.addContent(Poms.element(context, "groupId", excluded.getGroupId()));
            exclusion.addContent(Poms.element(context, "artifactId", excluded.getArtifactId()));
            container.addContent(exclusion);
        }
        return container;
    }

    /**
     * Brings an existing {@code <dependency>} in line with a new request, touching only the parts
     * that actually differ so the diff stays small.
     *
     * @return whether anything changed
     */
    public static boolean update(
            Element dependency, Coordinate coordinate, DependencyOptions options, String indentUnit) {
        boolean changed = false;
        changed |= set(dependency, "version", coordinate.getVersion(), indentUnit);
        changed |= set(dependency, "type", coordinate.getType(), indentUnit);
        changed |= set(dependency, "classifier", coordinate.getClassifier(), indentUnit);
        changed |= set(dependency, "scope", options.scope(), indentUnit);
        if (options.optional() != null) {
            changed |= set(dependency, "optional", options.optional() ? "true" : null, indentUnit);
        }
        if (!options.exclusions().isEmpty()) {
            Poms.setChildOrdered(
                    dependency, buildExclusions(dependency, options.exclusions()), ELEMENT_ORDER, indentUnit);
            changed = true;
        }
        return changed;
    }

    /**
     * Sets or removes a child of {@code <dependency>}. A {@code null} value leaves an existing
     * child alone: the caller did not ask about it.
     */
    private static boolean set(Element dependency, String name, String value, String indentUnit) {
        if (value == null) {
            return false;
        }
        if (value.equals(Poms.childText(dependency, name))) {
            return false;
        }
        Poms.setChildTextOrdered(dependency, name, value, ELEMENT_ORDER, indentUnit);
        return true;
    }

    /** Removes a child of {@code <dependency>}, e.g. a {@code <version>} now covered by a BOM. */
    public static boolean unset(Element dependency, String name) {
        return Poms.removeChild(dependency, name);
    }

    /** A one-line description used in log output and selection prompts. */
    public static String describe(Element dependency) {
        StringBuilder text = new StringBuilder(coordinateOf(dependency).toString());
        String scope = Poms.childText(dependency, "scope");
        if (scope != null) {
            text.append(" (").append(scope).append(')');
        }
        return text.toString();
    }
}
