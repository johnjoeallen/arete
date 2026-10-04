package net.dublinux.arete.engine.report;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How a shared definition is reached. A finding on a component (a schema, a response) is reported once, at the
 * component, however many operations lead to it; this lists the routes in from each operation, such as
 * {@code POST /orders → Order → Address}, so the reader can see why it matters. Built from the same reading of the
 * text as {@link PointerLocator}, following same-document {@code $ref}s only.
 */
final class RefPaths {
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    /** Component or path owner of a {@code $ref}, to the owners that name it, in document order. */
    private final Map<String, Set<String>> referrers = new LinkedHashMap<>();

    private RefPaths(Node root) {
        if (root != null) walk(root, "");
    }

    /** Reads {@code text}; if it cannot be read, every lookup answers an empty route. */
    static RefPaths of(String text) {
        if (text == null || text.isBlank()) return new RefPaths(null);
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(50);
            return new RefPaths(new Yaml(options).compose(new StringReader(text)));
        } catch (RuntimeException e) {
            return new RefPaths(null);
        }
    }

    private void walk(Node node, String pointer) {
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode key)) continue;
                String name = key.getValue();
                if ("$ref".equals(name) && tuple.getValueNode() instanceof ScalarNode ref && ref.getValue().startsWith("#/components/")) {
                    String target = owner(ref.getValue().substring(1));
                    String from = owner(pointer);
                    if (target != null && from != null && !target.equals(from)) referrers.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(from);
                } else {
                    walk(tuple.getValueNode(), pointer + "/" + name.replace("~", "~0").replace("/", "~1"));
                }
            }
        } else if (node instanceof SequenceNode sequence) {
            for (int i = 0; i < sequence.getValue().size(); i++) walk(sequence.getValue().get(i), pointer + "/" + i);
        }
    }

    /** The component, operation or path a pointer lies within; null for anywhere else. */
    private static String owner(String pointer) {
        if (pointer == null) return null;
        String[] tokens = pointer.split("/", -1);   // tokens[0] is empty
        if (tokens.length >= 4 && tokens[1].equals("components")) return "/components/" + tokens[2] + "/" + tokens[3];
        if (tokens.length >= 3 && tokens[1].equals("paths")) {
            return tokens.length >= 4 && METHODS.contains(tokens[3]) ? "/paths/" + tokens[2] + "/" + tokens[3] : "/paths/" + tokens[2];
        }
        return null;
    }

    /**
     * Every way into the component that holds {@code pointer}: one route per operation (or path's shared
     * parameters) that reaches it, each the shortest from that operation, as readable labels with the operation
     * first and the component last. Shortest routes come first. Empty when the pointer is not inside a component,
     * or nothing reachable from a path refers to it.
     */
    List<List<String>> routesTo(String pointer) {
        String start = owner(pointer);
        if (start == null || !start.startsWith("/components/")) return List.of();
        Map<String, String> next = new HashMap<>();   // owner to the owner it leads towards
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        Set<String> seen = new LinkedHashSet<>(List.of(start));
        List<List<String>> routes = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String referrer : referrers.getOrDefault(current, Set.of())) {
                if (!seen.add(referrer)) continue;
                next.put(referrer, current);
                if (referrer.startsWith("/paths/")) routes.add(route(referrer, next));
                else queue.add(referrer);
            }
        }
        return Collections.unmodifiableList(routes);
    }

    private static List<String> route(String from, Map<String, String> next) {
        List<String> labels = new ArrayList<>();
        for (String at = from; at != null; at = next.get(at)) labels.add(label(at));
        return Collections.unmodifiableList(labels);
    }

    private static String label(String owner) {
        String[] tokens = owner.split("/", -1);
        if (tokens[1].equals("components")) return tokens[2].equals("schemas") ? unescape(tokens[3]) : tokens[2] + "/" + unescape(tokens[3]);
        String path = unescape(tokens[2]);
        return tokens.length > 3 ? tokens[3].toUpperCase(java.util.Locale.ROOT) + " " + path : path;
    }

    private static String unescape(String token) { return token.replace("~1", "/").replace("~0", "~"); }
}
