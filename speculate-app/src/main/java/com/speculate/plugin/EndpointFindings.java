package com.speculate.plugin;

import speculate.validation.spi.Severity;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Groups validation findings by the endpoint (HTTP method + path) their
 * JSON Pointer location falls under, for the per-endpoint severity badges
 * shown on the spec page instead of one long itemized findings list.
 */
public final class EndpointFindings {

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private EndpointFindings() {
    }

    /**
     * Keyed by {@code "<METHOD> <path>"} (e.g. {@code "GET /items/{id}"}),
     * matching {@code EndpointView.method() + " " + EndpointView.path()}. A
     * finding whose pointer isn't under a specific operation — e.g. one
     * against {@code info} or a shared component schema — isn't
     * attributable to any single endpoint and is simply not counted here.
     */
    public static Map<String, SeverityCounts> byEndpoint(List<AttributedViolation> violations) {
        Map<String, EnumMap<Severity, Long>> counts = new LinkedHashMap<>();
        for (AttributedViolation av : violations) {
            String key = endpointKey(av.violation().getPointer());
            if (key == null) {
                continue;
            }
            counts.computeIfAbsent(key, k -> new EnumMap<>(Severity.class))
                    .merge(av.violation().getSeverity(), 1L, Long::sum);
        }

        Map<String, SeverityCounts> result = new LinkedHashMap<>();
        counts.forEach((key, bySeverity) -> result.put(key, SeverityCounts.of(bySeverity)));
        return result;
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
