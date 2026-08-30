package net.dublinux.arete.validation.policy;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record PolicyBundle(Map<String, PolicyRule> rules, Map<String, Policy> policies, Map<String, Matcher> matchers) {
    PolicyBundle {
        matchers = Collections.unmodifiableMap(new LinkedHashMap<>(matchers));
        policies = Collections.unmodifiableMap(new LinkedHashMap<>(policies));
        rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }
    Policy policyOrDefault(String requestedId) {
        Policy selected = policies.get(requestedId);
        return selected != null ? selected : policies.values().iterator().next();
    }
}

record PolicyRule(String id, String title, String category, String matcherId, String scope, Map<String, Object> parameters,
            String documentationMarkdown) {
    Map<String, Object> asMap() { return Map.of("id", id, "scope", scope, "parameters", parameters); }
}

record Matcher(String id, String language, String source, List<String> scopes, Map<String, ParameterDefinition> parameters) { }
record ParameterDefinition(String type, boolean required, List<String> values) { }
record Policy(String id, Map<String, PolicyDisposition> dispositions) {
    Policy {
        // Policy declaration order is report order. Map.copyOf deliberately
        // makes no iteration-order promise, so retain the YAML LinkedHashMap.
        dispositions = Collections.unmodifiableMap(new LinkedHashMap<>(dispositions));
    }
}
sealed interface PolicyDisposition permits Deduction, Prohibited {
    Map<String, Object> parameters();
}
record Deduction(double points, Map<String, Object> parameters) implements PolicyDisposition {
    Deduction {
        parameters = Map.copyOf(parameters);
    }
    Deduction(double points) { this(points, Map.of()); }
}
record Prohibited(Map<String, Object> parameters) implements PolicyDisposition {
    Prohibited {
        parameters = Map.copyOf(parameters);
    }
    Prohibited() { this(Map.of()); }
}
record Diagnostic(String pointer, String path, String message) { }

final class BundleValidationException extends RuntimeException {
    BundleValidationException(String message) { super(message); }
}

final class MatcherEvaluationException extends RuntimeException {
    MatcherEvaluationException(String message) { super(message); }
    MatcherEvaluationException(String message, Throwable cause) { super(message, cause); }
}
