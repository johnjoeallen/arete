package com.speculate.validation.policy;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record PolicyBundle(Map<String, Rule> rules, Map<String, Policy> policies, Map<String, Detector> detectors) {
    PolicyBundle {
        rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        policies = Collections.unmodifiableMap(new LinkedHashMap<>(policies));
        detectors = Collections.unmodifiableMap(new LinkedHashMap<>(detectors));
    }
    Policy policyOrDefault(String requestedId) {
        Policy selected = policies.get(requestedId);
        return selected != null ? selected : policies.values().iterator().next();
    }
}

record Rule(String id, String title, String category, String detector, String scope, Map<String, Object> parameters,
            String documentationMarkdown) {
    Map<String, Object> asMap() { return Map.of("id", id, "scope", scope, "parameters", parameters); }
}

record Detector(String id, String language, String source, List<String> scopes, Map<String, ParameterDefinition> parameters) { }
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
record Occurrence(String pointer, String path, String message) { }

final class BundleValidationException extends RuntimeException {
    BundleValidationException(String message) { super(message); }
}

final class DetectorException extends RuntimeException {
    DetectorException(String message) { super(message); }
    DetectorException(String message, Throwable cause) { super(message, cause); }
}
