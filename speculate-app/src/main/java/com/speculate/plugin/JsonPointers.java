package com.speculate.plugin;

import org.springframework.stereotype.Component;

/**
 * Renders an RFC 6901 JSON Pointer as a readable breadcrumb for display in
 * templates. A pointer into e.g. the {@code "/pet"} path is correctly
 * escaped per spec as {@code ".../paths/~1pet/..."} (the path itself
 * contains "/", which RFC 6901 escapes as "~1" so it isn't mistaken for a
 * pointer delimiter), but that reads oddly to a human. This decodes each
 * token individually and joins them with a breadcrumb separator instead of
 * naively replacing "~1" with "/" across the whole string, which would
 * collide with the pointer's own delimiters and produce a stray "//".
 */
@Component
public class JsonPointers {

    public String toDisplayPath(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return pointer;
        }
        String[] segments = pointer.split("/", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < segments.length; i++) {
            if (result.length() > 0) {
                result.append(" › ");
            }
            result.append(unescape(segments[i]));
        }
        return result.toString();
    }

    private static String unescape(String segment) {
        return segment.replace("~1", "/").replace("~0", "~");
    }
}
