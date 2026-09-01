package net.dublinux.arete.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups scoring findings by the top-level {@code components} entry
 * their JSON Pointer location falls under, mirroring {@link
 * EndpointFindings} but for the Model tab's reusable definitions —
 * {@code /components/schemas/<name>}, {@code /components/requestBodies/<name>},
 * {@code /components/responses/<name>} — instead of an operation.
 */
public final class ComponentFindings {

    /** Every {@code components} category the Model tab renders and attributes findings to. */
    public static final List<String> CATEGORIES = List.of("schemas", "requestBodies", "responses");

    private ComponentFindings() {
    }

    /**
     * Keyed by the entry's name (e.g. {@code "Pet"}), matching a key of
     * {@code openApi.getComponents().getSchemas()} (or {@code
     * getRequestBodies()}/{@code getResponses()} for the other categories).
     * A finding whose pointer isn't under a specific entry of this category
     * — e.g. one against an endpoint, {@code info}, or a different
     * {@code components} category — isn't attributable here and doesn't
     * appear in the result.
     */
    public static Map<String, EndpointFindingsView> byComponent(String category, List<AttributedDiagnostic> diagnostics) {
        Map<String, List<AttributedDiagnostic>> byKey = new LinkedHashMap<>();
        for (AttributedDiagnostic av : diagnostics) {
            String key = componentKey(category, av.diagnostic().getPointer());
            if (key == null) {
                continue;
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(av);
        }

        Map<String, EndpointFindingsView> result = new LinkedHashMap<>();
        byKey.forEach((key, forComponent) -> result.put(key, new EndpointFindingsView(SeverityCounts.from(forComponent), forComponent)));
        return result;
    }

    /** {@code null} unless {@code pointer} is rooted at a specific {@code /components/<category>/<name>}. */
    static String componentKey(String category, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return null;
        }
        // A JSON Pointer starts with '/', so splitting on '/' gives an empty
        // segments[0]; [1]=components, [2]=the category, [3]=the escaped entry name.
        String[] segments = pointer.split("/", -1);
        if (segments.length < 4 || !"components".equals(segments[1]) || !category.equals(segments[2])) {
            return null;
        }
        return unescape(segments[3]);
    }

    /** Reverses JSON Pointer's escaping (RFC 6901 §3) — order matters: "~1" before "~0". */
    private static String unescape(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }
}
