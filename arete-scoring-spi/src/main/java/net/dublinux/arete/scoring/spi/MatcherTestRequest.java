package net.dublinux.arete.scoring.spi;

import java.util.Map;

/** Input supplied by the matcher workbench to a plugin that supports it. */
public record MatcherTestRequest(String language, String matcherId, String source,
        String scope, Map<String, Object> parameters, String spec) {
    public MatcherTestRequest {
        if (language == null || language.isBlank()) throw new IllegalArgumentException("language is required");
        if (matcherId == null || matcherId.isBlank()) throw new IllegalArgumentException("matcher id is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("matcher source is required");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope is required");
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("spec is required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
