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
                    int index;
                    try {
                        index = Integer.parseInt(key);
                    } catch (NumberFormatException e) {
                        break;
                    }
                    if (index < 0 || index >= sequence.getValue().size()) break;
                    node = sequence.getValue().get(index);
                    mark = node.getStartMark();
                } else {
                    break;
                }
            }
        }
        return new Location(mark.getLine() + 1, mark.getColumn() + 1);
    }
}
