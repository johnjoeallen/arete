package net.dublinux.arete.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Groups validation findings by the endpoint (HTTP method + path) their
 * JSON Pointer location falls under: severity-badge counts for the
 * collapsed endpoint header, and the full finding list for when it's
 * expanded, instead of one long itemized list up front.
 */
public final class EndpointFindings {

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private EndpointFindings() {
    }

    /**
     * Keyed by {@code "<METHOD> <path>"} (e.g. {@code "GET /items/{id}"}),
     * matching {@code EndpointView.method() + " " + EndpointView.path()}. A
     * diagnostic is attributed to every endpoint it names — its {@code
     * pointer}'s own operation, if any, plus every entry in {@link
     * net.dublinux.arete.validation.spi.Diagnostic#getPaths()} — so a
     * rule a plugin reports once but flags as affecting several endpoints
     * (via {@code paths}, rather than emitting one {@code Diagnostic} per
     * endpoint) shows up on all of them, not just the one its {@code
     * pointer} happens to point at. A finding attributable to neither —
     * e.g. one against {@code info} or a shared component schema, with no
     * {@code paths} entries either — doesn't appear here.
     */
    public static Map<String, EndpointFindingsView> byEndpoint(List<AttributedDiagnostic> diagnostics) {
        Map<String, List<AttributedDiagnostic>> byKey = new LinkedHashMap<>();
        for (AttributedDiagnostic av : diagnostics) {
            for (String key : endpointKeys(av.diagnostic())) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(av);
            }
        }

        Map<String, EndpointFindingsView> result = new LinkedHashMap<>();
        byKey.forEach((key, forEndpoint) -> result.put(key, new EndpointFindingsView(SeverityCounts.from(forEndpoint), forEndpoint)));
        return result;
    }

    /** Every endpoint key a diagnostic is attributable to — see {@link #byEndpoint}. */
    private static Set<String> endpointKeys(net.dublinux.arete.validation.spi.Diagnostic diagnostic) {
        Set<String> keys = new LinkedHashSet<>();
        String pointerKey = endpointKey(diagnostic.getPointer());
        if (pointerKey != null) {
            keys.add(pointerKey);
        }
        for (String path : diagnostic.getPaths()) {
            if (path != null && !path.isBlank()) {
                keys.add(normalizeMethod(path.trim()));
            }
        }
        return keys;
    }

    /** Upper-cases a leading HTTP method so a plugin-supplied {@code "get /items"} matches the {@code "GET /items"} keys built from a pointer. */
    private static String normalizeMethod(String pathEntry) {
        int spaceIndex = pathEntry.indexOf(' ');
        if (spaceIndex <= 0) {
            return pathEntry;
        }
        return pathEntry.substring(0, spaceIndex).toUpperCase(Locale.ROOT) + pathEntry.substring(spaceIndex);
    }

    /** {@code null} unless {@code pointer} is rooted at a specific {@code /paths/<path>/<method>} operation. */
    static String endpointKey(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return null;
        }
        // A JSON Pointer starts with '/', so splitting on '/' gives an empty
        // segments[0]; [1]=paths, [2]=the escaped path template, [3]=method.
        String[] segments = pointer.split("/", -1);
        if (segments.length < 4 || !"paths".equals(segments[1])) {
            return null;
        }
        String method = segments[3].toLowerCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
            return null;
        }
        return method.toUpperCase(Locale.ROOT) + " " + unescape(segments[2]);
    }

    /** Reverses JSON Pointer's escaping (RFC 6901 §3) — order matters: "~1" before "~0". */
    private static String unescape(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }
}
