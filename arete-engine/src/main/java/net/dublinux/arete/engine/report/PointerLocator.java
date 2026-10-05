package net.dublinux.arete.engine.report;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;

/**
 * Finds the line and column of a JSON Pointer in a spec's text. SnakeYAML keeps source marks, and JSON is
 * YAML, so one reader serves both. A pointer that names something absent (a missing summary, say) is
 * located at its nearest ancestor that exists, which is where a reader would add it.
 */
public final class PointerLocator {
    /** A 1-based line and column. */
    public record Location(int line, int column) { }

    private final Node root;

    /**
     * An element of a list. Pointers name list elements by what they are, not where they sit, so that adding one
     * does not move the rest: a number is an index (as in plain JSON Pointer); {@code in:name} (and
     * {@code in:name#2} for a repeat) is a parameter; any other text matches an element's {@code name} (a tag) or
     * {@code url} (a server). A parameter written as a {@code $ref} is matched by the parameter it points to.
     */
    private Node element(SequenceNode sequence, String key) {
        try {
            int index = Integer.parseInt(key);
            return index >= 0 && index < sequence.getValue().size() ? sequence.getValue().get(index) : null;
        } catch (NumberFormatException notAnIndex) {
            // fall through to naming
        }
        String wanted = key;
        int wantedOccurrence = 1;
        int hash = key.lastIndexOf('#');
        if (hash > 0 && key.substring(hash + 1).matches("[0-9]+")) {
            wanted = key.substring(0, hash);
            wantedOccurrence = Integer.parseInt(key.substring(hash + 1));
        }
        int seen = 0;
        for (Node item : sequence.getValue()) {
            if (!(item instanceof MappingNode mapping)) continue;
            MappingNode described = mapping;
            String ref = scalar(mapping, "$ref");
            if (ref != null && ref.startsWith("#/")) {
                Node target = byPointer(ref.substring(1));
                if (target instanceof MappingNode targetMapping) described = targetMapping;
            }
            String in = scalar(described, "in");
            String name = scalar(described, "name");
            String url = scalar(described, "url");
            if (in != null && name != null) {
                if ((in + ":" + name).equals(wanted) && ++seen == wantedOccurrence) return item;
            } else if (wanted.equals(name) || wanted.equals(url)) {
                return item;
            }
        }
        return null;
    }

    private Node byPointer(String pointer) {
        Node node = root;
        for (String token : pointer.substring(pointer.startsWith("/") ? 1 : 0).split("/", -1)) {
            String key = token.replace("~1", "/").replace("~0", "~");
            if (node instanceof MappingNode mapping) {
                Node next = null;
                for (NodeTuple tuple : mapping.getValue()) {
                    if (tuple.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) next = tuple.getValueNode();
                }
                if (next == null) return null;
                node = next;
            } else {
                return null;
            }
        }
        return node;
    }

    private static String scalar(MappingNode mapping, String key) {
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode k && k.getValue().equals(key) && tuple.getValueNode() instanceof ScalarNode v) return v.getValue();
        }
        return null;
    }

    private PointerLocator(Node root) { this.root = root; }

    /** Reads {@code text}; if it cannot be read, every lookup answers null. */
    public static PointerLocator of(String text) {
        if (text == null || text.isBlank()) return new PointerLocator(null);
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(50);
            return new PointerLocator(new Yaml(options).compose(new StringReader(text)));
        } catch (RuntimeException e) {
            return new PointerLocator(null);
        }
    }

    /**
     * The place {@code pointer} (or its nearest existing ancestor) starts: the line of the key for a field, of the
     * item for an array element. Null when the text is unreadable.
     */
    public Location locate(String pointer) {
        if (root == null) return null;
        Node node = root;
        Mark mark = root.getStartMark();
        if (pointer != null && !pointer.isEmpty() && !pointer.equals("/")) {
            for (String token : pointer.substring(pointer.startsWith("/") ? 1 : 0).split("/", -1)) {
                String key = token.replace("~1", "/").replace("~0", "~");
                if (node instanceof MappingNode mapping) {
                    Node next = null;
                    for (NodeTuple tuple : mapping.getValue()) {
                        if (tuple.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) {
                            next = tuple.getValueNode();
                            mark = tuple.getKeyNode().getStartMark();
                            break;
                        }
                    }
                    if (next == null) break;
                    node = next;
                } else if (node instanceof SequenceNode sequence) {
                    Node item = element(sequence, key);
                    if (item == null) break;
                    node = item;
                    mark = node.getStartMark();
                } else {
                    break;
                }
            }
        }
        return new Location(mark.getLine() + 1, mark.getColumn() + 1);
    }
}
