package net.dublinux.arete.validation.policy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Hand-written Java ports of a representative slice of the bundled matchers,
 * used only by the performance comparison in {@link DistillGroovyParityTest}.
 * Each takes the same {@code (api, rule)} map contract the Distill and Groovy
 * engines receive and returns the same {@link Diagnostic} list.
 *
 * <p>The set spans the cost spectrum seen in the Groovy/Distill sweep:
 * {@code hostname} (trivial, regex-bound), {@code date-time-name} and
 * {@code status-class} (linear model walks), {@code operation-semantics}
 * (regex-heavy filter — Groovy's worst case), and {@code path-set}
 * (deliberately O(n²) — Distill's worst case).
 */
final class JavaMatchers {
    private JavaMatchers() { }

    interface Matcher extends BiFunction<Map<String, Object>, Map<String, Object>, List<Diagnostic>> { }

    static final Map<String, Matcher> BY_ID = Map.of(
            "hostname", JavaMatchers::hostname,
            "date-time-name", JavaMatchers::dateTimeName,
            "status-class", JavaMatchers::statusClass,
            "operation-semantics", JavaMatchers::operationSemantics,
            "path-set", JavaMatchers::pathSet);

    // --- accessors -------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> node, String key) {
        Object value = node.get(key);
        return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> node, String key) {
        Object value = node.get(key);
        return value instanceof List<?> l ? (List<String>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Map<String, Object> rule) {
        Object value = rule.get("parameters");
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object value) { return value == null ? null : value.toString(); }

    // --- matchers -------------------------------------------------------

    private static final Pattern LOWER_HYPHEN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** api.servers whose host is not lowercase-hyphenated. */
    private static List<Diagnostic> hostname(Map<String, Object> api, Map<String, Object> rule) {
        List<Diagnostic> out = new ArrayList<>();
        for (String url : strings(api, "servers")) {
            String host = uriHost(url);
            if (!(host != null && LOWER_HYPHEN.matcher(host).matches())) {
                out.add(new Diagnostic("/servers", url, "Server hostname is not lowercase hyphenated"));
            }
        }
        return out;
    }

    /** string/date-time properties whose name does not end with the configured suffix. */
    private static List<Diagnostic> dateTimeName(Map<String, Object> api, Map<String, Object> rule) {
        String suffix = String.valueOf(params(rule).get("suffix"));
        List<Diagnostic> out = new ArrayList<>();
        for (Map<String, Object> schema : list(api, "schemas")) {
            for (Map<String, Object> property : list(schema, "properties")) {
                if ("string".equals(property.get("type")) && "date-time".equals(property.get("format"))
                        && !String.valueOf(property.get("name")).endsWith(suffix)) {
                    out.add(new Diagnostic(str(property.get("pointer")), str(property.get("name")),
                            "Date-time property name does not end with " + suffix));
                }
            }
        }
        return out;
    }

    /** documented 5xx responses, when forbidden == "server-error". */
    private static List<Diagnostic> statusClass(Map<String, Object> api, Map<String, Object> rule) {
        if (!"server-error".equals(params(rule).get("forbidden"))) return List.of();
        List<Diagnostic> out = new ArrayList<>();
        for (Map<String, Object> path : list(api, "paths")) {
            for (Map<String, Object> operation : list(path, "operationDetails")) {
                for (Map<String, Object> response : list(operation, "responses")) {
                    long code = parseInt(str(response.get("status")), -1);
                    if (code >= 500 && code < 600) {
                        out.add(new Diagnostic(str(operation.get("pointer")),
                                operation.get("method") + " " + path.get("path") + " " + response.get("status"),
                                "Documents a server-error (" + response.get("status")
                                        + ") response; these should be omitted from the contract"));
                    }
                }
            }
        }
        return out;
    }

    private static final Pattern MUTATING =
            Pattern.compile("(?i).*\\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\\b.*");
    private static final Pattern IDENTIFIED_RESOURCE = Pattern.compile(".+/\\{[^}]+\\}.*");
    private static final Pattern REPLACING = Pattern.compile("(?i).*\\b(replace|replacement)\\b.*");
    private static final Pattern UPDATING = Pattern.compile("(?i).*\\b(partial|patch|update)\\b.*");

    /** operations whose method and summary/path wording disagree, per the configured check. */
    private static List<Diagnostic> operationSemantics(Map<String, Object> api, Map<String, Object> rule) {
        Map<String, Object> parameters = params(rule);
        String method = str(parameters.get("method"));
        String expected = str(parameters.get("expected"));
        String match = str(parameters.get("match"));
        List<Diagnostic> out = new ArrayList<>();
        for (Map<String, Object> path : list(api, "paths")) {
            String pathText = str(path.get("path"));
            for (Map<String, Object> operation : list(path, "operationDetails")) {
                String verb = str(operation.get("method"));
                if (method != null && !method.equals(verb)) continue;
                String phrase = pathText + " " + operation.get("summary");
                boolean unsafeGet = "GET".equals(verb) && MUTATING.matcher(phrase).matches();
                boolean replacingPost = "POST".equals(verb) && IDENTIFIED_RESOURCE.matcher(pathText).matches()
                        && REPLACING.matcher(phrase).matches();
                boolean flagged =
                        ("safe".equals(expected) && unsafeGet)
                        || ("full-resource-replacement".equals(match) && replacingPost)
                        || ("partial-update".equals(match) && "PUT".equals(verb) && UPDATING.matcher(phrase).matches())
                        || ("inconsistent-method-resource-semantics".equals(match) && (unsafeGet || replacingPost));
                if (flagged) {
                    out.add(new Diagnostic(str(operation.get("pointer")), verb + " " + pathText,
                            operationMessage(expected, match)));
                }
            }
        }
        return out;
    }

    private static String operationMessage(String expected, String match) {
        if ("safe".equals(expected)) return "GET operation appears to mutate state";
        if ("full-resource-replacement".equals(match)) return "POST appears to replace an identified resource";
        if ("partial-update".equals(match)) return "PUT appears to perform a partial update";
        if ("inconsistent-method-resource-semantics".equals(match)) return "HTTP method and resource semantics appear inconsistent";
        return "Supported operation semantics are unclear";
    }

    /** paths that are structurally identical to an earlier path (O(n²), as the DSL is). */
    private static List<Diagnostic> pathSet(Map<String, Object> api, Map<String, Object> rule) {
        List<Map<String, Object>> paths = list(api, "paths");
        List<Diagnostic> out = new ArrayList<>();
        for (Map<String, Object> path : paths) {
            String shape = shape(str(path.get("path")));
            Map<String, Object> firstMatch = null;
            for (Map<String, Object> other : paths) {
                if (shape.equals(shape(str(other.get("path"))))) { firstMatch = other; break; }
            }
            if (firstMatch != null && firstMatch != path) {
                out.add(new Diagnostic(str(path.get("pointer")), str(path.get("path")),
                        "Path is structurally identical to " + firstMatch.get("path")));
            }
        }
        return out;
    }

    private static String shape(String path) {
        String[] segments = path.split(Pattern.quote("/"));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) out.append('/');
            String s = segments[i];
            out.append(s.startsWith("{") && s.endsWith("}") ? "{}" : s);
        }
        return out.toString();
    }

    // --- builtins mirrored from Distill ---------------------------------

    private static String uriHost(String url) {
        try { return new URI(url).getHost(); }
        catch (Exception e) { return null; }
    }

    private static long parseInt(String text, long fallback) {
        try { return Long.parseLong(text.trim()); }
        catch (RuntimeException e) { return fallback; }
    }
}
