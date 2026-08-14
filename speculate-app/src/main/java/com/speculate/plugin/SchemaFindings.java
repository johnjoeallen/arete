package com.speculate.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups validation findings by the top-level component schema their JSON
 * Pointer location falls under, mirroring {@link EndpointFindings} but for
 * the Model tab's {@code /components/schemas/<name>} entries instead of an
 * operation.
 */
public final class SchemaFindings {

    private SchemaFindings() {
    }

    /**
     * Keyed by schema name (e.g. {@code "Pet"}), matching a key of {@code
     * openApi.getComponents().getSchemas()}. A finding whose pointer isn't
     * under a specific top-level schema — e.g. one against an endpoint or
     * {@code info} — isn't attributable to any schema and doesn't appear
     * here.
     */
    public static Map<String, EndpointFindingsView> bySchema(List<AttributedViolation> violations) {
        Map<String, List<AttributedViolation>> byKey = new LinkedHashMap<>();
        for (AttributedViolation av : violations) {
            String key = schemaKey(av.violation().getPointer());
            if (key == null) {
                continue;
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(av);
        }

        Map<String, EndpointFindingsView> result = new LinkedHashMap<>();
        byKey.forEach((key, forSchema) -> result.put(key, new EndpointFindingsView(SeverityCounts.from(forSchema), forSchema)));
        return result;
    }

    /** {@code null} unless {@code pointer} is rooted at a specific {@code /components/schemas/<name>}. */
    static String schemaKey(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return null;
        }
        // A JSON Pointer starts with '/', so splitting on '/' gives an empty
        // segments[0]; [1]=components, [2]=schemas, [3]=the escaped schema name.
        String[] segments = pointer.split("/", -1);
        if (segments.length < 4 || !"components".equals(segments[1]) || !"schemas".equals(segments[2])) {
            return null;
        }
        return unescape(segments[3]);
    }

    /** Reverses JSON Pointer's escaping (RFC 6901 §3) — order matters: "~1" before "~0". */
    private static String unescape(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }
}
