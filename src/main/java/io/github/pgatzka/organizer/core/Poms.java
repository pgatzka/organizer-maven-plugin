package io.github.pgatzka.organizer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;

/**
 * Namespace-tolerant navigation and indentation-aware mutation of a POM tree.
 *
 * <p>Every insertion carries the whitespace the document already uses, so a change shows up in
 * {@code git diff} as the added lines and nothing else.
 */
public final class Poms {

    private Poms() {}

    // ---------------------------------------------------------------- navigation

    /**
     * Returns the named child, looking in the parent's namespace first and then outside any
     * namespace, so POMs that omit {@code xmlns} work the same as ones that declare it.
     */
    public static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        Element found = parent.getChild(name, parent.getNamespace());
        if (found == null && parent.getNamespace() != Namespace.NO_NAMESPACE) {
            found = parent.getChild(name, Namespace.NO_NAMESPACE);
        }
        return found;
    }

    /** Walks a chain of child names, returning empty as soon as a link is missing. */
    public static Optional<Element> path(Element parent, String... names) {
        Element current = parent;
        for (String name : names) {
            current = child(current, name);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(current);
    }

    /** All named children, in document order. */
    public static List<Element> children(Element parent, String name) {
        if (parent == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>(parent.getChildren(name, parent.getNamespace()));
        if (result.isEmpty() && parent.getNamespace() != Namespace.NO_NAMESPACE) {
            result = new ArrayList<>(parent.getChildren(name, Namespace.NO_NAMESPACE));
        }
        return result;
    }

    /** The trimmed text of a named child, or {@code null} when the child is absent. */
    public static String childText(Element parent, String name) {
        Element found = child(parent, name);
        return found == null ? null : found.getTextTrim();
    }

    /** The trimmed text of a named child, or {@code fallback} when the child is absent or blank. */
    public static String childText(Element parent, String name, String fallback) {
        String text = childText(parent, name);
        return text == null || text.isEmpty() ? fallback : text;
    }

    // ---------------------------------------------------------------- creation

    /** Creates a detached element in the same namespace as {@code sibling}. */
    public static Element element(Element sibling, String name) {
        return new Element(name, sibling.getNamespace());
    }

    /** Creates a detached element with text content, in the same namespace as {@code sibling}. */
    public static Element element(Element sibling, String name, String text) {
        Element element = element(sibling, name);
        element.setText(text);
        return element;
    }

    // ---------------------------------------------------------------- mutation

    /**
     * Appends {@code child} as the last element child of {@code parent}, indented to match the
     * document.
     */
    public static void append(Element parent, Element child, String indentUnit) {
        int depth = depthOf(parent) + 1;
        indentSubtree(child, depth, indentUnit);
        ensureClosingIndent(parent, indentUnit);
        int at = parent.getContentSize() - 1;
        parent.addContent(at, child);
        parent.addContent(at, new Text("\n" + indentUnit.repeat(depth)));
    }

    /**
     * Inserts {@code child} directly before {@code reference}, indented to match the document.
     * {@code reference} must be a child of {@code parent}.
     */
    public static void insertBefore(Element parent, Element child, Element reference, String indentUnit) {
        int index = parent.indexOf(reference);
        if (index < 0) {
            throw new IllegalArgumentException("Reference element is not a child of " + parent.getName());
        }
        int depth = depthOf(parent) + 1;
        indentSubtree(child, depth, indentUnit);
        parent.addContent(index, new Text("\n" + indentUnit.repeat(depth)));
        parent.addContent(index, child);
    }

    /**
     * Inserts {@code child} at the position that keeps the named element children sorted by
     * {@code key}. Falls back to appending when the existing children are not already sorted, so a
     * hand-ordered section is never shuffled behind the user's back.
     */
    public static void insertSorted(
            Element parent,
            Element child,
            String childName,
            Comparator<Element> order,
            String indentUnit) {
        List<Element> existing = children(parent, childName);
        boolean sorted = isSorted(existing, order);
        if (!sorted) {
            append(parent, child, indentUnit);
            return;
        }
        for (Element candidate : existing) {
            if (order.compare(child, candidate) < 0) {
                insertBefore(parent, child, candidate, indentUnit);
                return;
            }
        }
        append(parent, child, indentUnit);
    }

    private static boolean isSorted(List<Element> elements, Comparator<Element> order) {
        for (int i = 1; i < elements.size(); i++) {
            if (order.compare(elements.get(i - 1), elements.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the named child of {@code parent}, creating and appending it when absent.
     */
    public static Element childOrCreate(Element parent, String name, String indentUnit) {
        Element found = child(parent, name);
        if (found != null) {
            return found;
        }
        Element created = element(parent, name);
        append(parent, created, indentUnit);
        return created;
    }

    /**
     * Returns the element at the end of a chain of child names, creating every missing link.
     */
    public static Element pathOrCreate(Element parent, String indentUnit, String... names) {
        Element current = parent;
        for (String name : names) {
            current = childOrCreate(current, name, indentUnit);
        }
        return current;
    }

    /** Sets the text of a named child, creating the child when it does not exist yet. */
    public static void setChildText(Element parent, String name, String text, String indentUnit) {
        Element found = child(parent, name);
        if (found == null) {
            append(parent, element(parent, name, text), indentUnit);
        } else {
            found.setText(text);
        }
    }

    /**
     * Removes {@code child} from {@code parent} together with the whitespace that indented it, so
     * no blank line is left behind.
     */
    public static void remove(Element parent, Element child) {
        int index = parent.indexOf(child);
        if (index < 0) {
            return;
        }
        parent.removeContent(child);
        if (index > 0) {
            Content previous = parent.getContent(index - 1);
            if (isWhitespace(previous)) {
                parent.removeContent(index - 1);
            }
        }
    }

    /** Removes a named child, if present. Returns whether anything was removed. */
    public static boolean removeChild(Element parent, String name) {
        Element found = child(parent, name);
        if (found == null) {
            return false;
        }
        remove(parent, found);
        return true;
    }

    /**
     * Removes {@code element} and every ancestor that is left without element children, stopping
     * before the document element. Keeps the POM free of empty {@code <dependencies/>} husks.
     */
    public static void removeAndPrune(Element element) {
        Element parent = element.getParentElement();
        if (parent == null) {
            return;
        }
        remove(parent, element);
        while (parent != null && parent.getParentElement() != null && !hasElementChildren(parent)) {
            Element grandParent = parent.getParentElement();
            remove(grandParent, parent);
            parent = grandParent;
        }
    }

    /** Whether the element has at least one element child. */
    public static boolean hasElementChildren(Element element) {
        return !element.getChildren().isEmpty();
    }

    // ---------------------------------------------------------------- indentation

    /** Number of ancestors between {@code element} and the document element. */
    public static int depthOf(Element element) {
        int depth = 0;
        Element current = element.getParentElement();
        while (current != null) {
            depth++;
            current = current.getParentElement();
        }
        return depth;
    }

    /**
     * Adds whitespace to a freshly built, still detached subtree so that it lines up with a parent
     * at {@code depth} levels of indentation.
     */
    public static void indentSubtree(Element element, int depth, String indentUnit) {
        List<Element> childElements = new ArrayList<>(element.getChildren());
        if (childElements.isEmpty()) {
            return;
        }
        for (Element child : childElements) {
            indentSubtree(child, depth + 1, indentUnit);
            int index = element.indexOf(child);
            element.addContent(index, new Text("\n" + indentUnit.repeat(depth + 1)));
        }
        element.addContent(new Text("\n" + indentUnit.repeat(depth)));
    }

    /**
     * Makes sure the parent's closing tag sits on its own line, so appended children can simply be
     * inserted before the final whitespace node.
     */
    private static void ensureClosingIndent(Element parent, String indentUnit) {
        String closingIndent = "\n" + indentUnit.repeat(depthOf(parent));
        int size = parent.getContentSize();
        if (size == 0) {
            parent.addContent(new Text(closingIndent));
            return;
        }
        Content last = parent.getContent(size - 1);
        if (!isWhitespace(last)) {
            parent.addContent(new Text(closingIndent));
            return;
        }
        Text text = (Text) last;
        if (!text.getText().contains("\n")) {
            text.setText(closingIndent);
        }
    }

    private static boolean isWhitespace(Content content) {
        return content instanceof Text text && text.getText().isBlank();
    }
}
