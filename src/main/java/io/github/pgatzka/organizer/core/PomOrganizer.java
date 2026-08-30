package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Text;

/**
 * Puts a POM in order: sections in the sequence the POM reference recommends, and the lists inside
 * them sorted.
 *
 * <p>Reordering moves whole blocks. A comment travels with the element that follows it, which is
 * the convention POMs are written in, and the blank lines that separate entries are kept unless
 * asked otherwise. Anything the POM reference does not prescribe an order for — {@code
 * <properties>} beyond its own sorting, and {@code <configuration>}, where the elements are a
 * plugin's own vocabulary — is left exactly as written.
 */
public final class PomOrganizer {

    /** Elements whose children are a plugin's own vocabulary, not POM structure. */
    private static final Set<String> OPAQUE = Set.of("configuration", "reportSets");

    /** Sections whose entries are a list of the same element, sorted rather than ordered. */
    private static final Set<String> SORTED_LISTS = Set.of("dependencies", "plugins", "modules", "exclusions");

    private final Options options;

    public PomOrganizer(Options options) {
        this.options = options;
    }

    /** Reorders the whole document. */
    public void organize(PomDocument pom) {
        organize(pom.getRoot(), pom.getIndentUnit());
    }

    /** Reorders {@code element} and everything below it. */
    void organize(Element element, String indentUnit) {
        if (OPAQUE.contains(element.getName())) {
            return;
        }
        Comparator<Element> order = orderFor(element);
        if (order != null) {
            reorder(element, order, indentUnit);
        }
        for (Element child : new ArrayList<>(element.getChildren())) {
            organize(child, indentUnit);
        }
    }

    /** How the children of {@code element} should be arranged, or {@code null} to leave them. */
    private Comparator<Element> orderFor(Element element) {
        String name = element.getName();

        if (SORTED_LISTS.contains(name)) {
            return sortedListOrder(name);
        }
        if ("properties".equals(name)) {
            return options.sortProperties() ? Comparator.comparing(Element::getName) : null;
        }
        List<String> schema = PomSchema.orderFor(name);
        if (!schema.isEmpty() && options.reorderSections()) {
            return schemaOrder(schema);
        }
        return null;
    }

    private Comparator<Element> sortedListOrder(String name) {
        return switch (name) {
            case "dependencies" -> options.dependencyOrder();
            case "plugins" -> options.sortPlugins()
                    ? Comparator.comparing((Element e) -> Poms.childText(e, "groupId", Plugins.DEFAULT_GROUP_ID))
                            .thenComparing(e -> Poms.childText(e, "artifactId", ""))
                    : null;
            case "modules" -> options.sortModules() ? Comparator.comparing(Element::getTextTrim) : null;
            case "exclusions" -> options.dependencyOrder() == null ? null : Dependencies.BY_COORDINATE;
            default -> null;
        };
    }

    /** Orders by position in {@code schema}, leaving anything unlisted at the end, in place. */
    private static Comparator<Element> schemaOrder(List<String> schema) {
        return Comparator.comparingInt(element -> {
            int position = schema.indexOf(element.getName());
            return position < 0 ? schema.size() : position;
        });
    }

    // ---------------------------------------------------------------- block moving

    /**
     * Rearranges the element children of {@code parent} according to {@code order}, carrying each
     * one's leading comments and blank line along with it.
     */
    private void reorder(Element parent, Comparator<Element> order, String indentUnit) {
        List<Block> blocks = split(parent);
        if (blocks.isEmpty()) {
            return;
        }
        List<Block> sorted = new ArrayList<>(blocks);
        // A stable sort, so entries that compare equal stay in the order they were written.
        sorted.sort((left, right) -> order.compare(left.element, right.element));
        if (sorted.equals(blocks)) {
            return;
        }
        rebuild(parent, sorted, indentUnit);
    }

    /**
     * Splits the content of {@code parent} into one block per element child, each carrying the
     * comments written above it, plus whether a blank line separated it from what came before.
     */
    private static List<Block> split(Element parent) {
        List<Block> blocks = new ArrayList<>();
        List<Piece> pending = new ArrayList<>();
        boolean blank = false;

        for (Content content : parent.getContent()) {
            if (content instanceof Text text) {
                if (!text.getText().isBlank()) {
                    // Mixed content: not something to rearrange.
                    return List.of();
                }
                blank = countNewlines(text.getText()) > 1;
            } else if (content instanceof Comment comment) {
                pending.add(new Piece(blank, comment));
                blank = false;
            } else if (content instanceof Element element) {
                pending.add(new Piece(blank, element));
                blocks.add(new Block(List.copyOf(pending), element));
                pending.clear();
                blank = false;
            } else {
                return List.of();
            }
        }
        // Comments after the last element belong to no block; leave the whole thing alone rather
        // than guess where they should end up.
        return pending.isEmpty() ? blocks : List.of();
    }

    /** Writes the blocks back into {@code parent}, re-indenting as it goes. */
    private void rebuild(Element parent, List<Block> blocks, String indentUnit) {
        String childIndent = "\n" + indentUnit.repeat(Poms.depthOf(parent) + 1);
        String closingIndent = "\n" + indentUnit.repeat(Poms.depthOf(parent));

        for (Block block : blocks) {
            for (Piece piece : block.pieces) {
                piece.content.detach();
            }
        }
        parent.removeContent();

        boolean first = true;
        for (Block block : blocks) {
            for (Piece piece : block.pieces) {
                boolean blank = piece.blank && options.keepBlankLines() && !first;
                parent.addContent(new Text((blank ? "\n" : "") + childIndent));
                parent.addContent(piece.content);
                first = false;
            }
        }
        parent.addContent(new Text(closingIndent));
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** One element child together with the comments and blank line written above it. */
    private record Block(List<Piece> pieces, Element element) {}

    /** A comment or element, and whether a blank line came before it. */
    private record Piece(boolean blank, Content content) {}

    /** What the organizer should and should not touch. */
    public record Options(
            boolean reorderSections,
            Comparator<Element> dependencyOrder,
            boolean sortPlugins,
            boolean sortModules,
            boolean sortProperties,
            boolean keepBlankLines) {

        /** Everything on, dependencies sorted by coordinate. */
        public static Options defaults() {
            return new Options(true, Dependencies.BY_COORDINATE, true, true, true, true);
        }

        /**
         * Builds the comparator for {@code -DsortDependencies}, a comma-separated list of
         * {@code <dependency>} child names such as {@code scope,groupId,artifactId}.
         *
         * @return the comparator, or {@code null} for {@code false} or {@code none}
         */
        public static Comparator<Element> dependencyOrder(String keys) {
            if (keys == null || keys.isBlank()) {
                return Dependencies.BY_COORDINATE;
            }
            String trimmed = keys.trim();
            if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("none")) {
                return null;
            }
            Comparator<Element> comparator = null;
            for (String key : trimmed.split(",")) {
                String field = key.trim();
                if (field.isEmpty()) {
                    continue;
                }
                if (!Dependencies.ELEMENT_ORDER.contains(field)) {
                    throw new IllegalArgumentException(
                            "Cannot sort dependencies by '" + field + "'. Choose from "
                                    + String.join(", ", Dependencies.ELEMENT_ORDER) + ".");
                }
                Comparator<Element> next = Comparator.comparing(element -> Poms.childText(element, field, ""));
                comparator = comparator == null ? next : comparator.thenComparing(next);
            }
            return comparator == null ? Dependencies.BY_COORDINATE : comparator;
        }
    }
}
