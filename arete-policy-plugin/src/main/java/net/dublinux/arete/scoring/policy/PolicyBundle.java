package net.dublinux.arete.scoring.policy;

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
/**
 * @param scoreLevel    the policy's suggested pass level for automated gating,
 *                      in the {@code blocker | error | score<NN} grammar, or
 *                      null if the policy states no opinion.
 * @param passingScore  the minimum overall score this policy considers a pass,
 *                      or null.
 * @param grades        score → grade label, ordered high threshold to low; a
 *                      score at or above a threshold earns that grade. Empty
 *                      if the policy defines no grade bands.
 */
record Policy(String id, Map<String, PolicyDisposition> dispositions, String scoreLevel,
        Double passingScore, Map<String, Double> grades) {
    Policy {
        // Policy declaration order is report order. Map.copyOf deliberately
        // makes no iteration-order promise, so retain the YAML LinkedHashMap.
        dispositions = Collections.unmodifiableMap(new LinkedHashMap<>(dispositions));
        grades = grades == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(grades));
    }

    Policy(String id, Map<String, PolicyDisposition> dispositions) {
        this(id, dispositions, null, null, Map.of());
    }

    /**
     * The grade label for a numeric score, or null if no bands are defined.
     * Within a band wide enough to divide, a score in the top third earns a
     * {@code +} and the bottom third a {@code -} (so {@code A:95} yields
     * {@code A-} at 96, {@code A} at 97, {@code A+} at 99). Below the lowest
     * band the grade is {@code F}.
     */
    String gradeFor(double score) {
        if (grades.isEmpty()) return null;
        List<Map.Entry<String, Double>> bands = new java.util.ArrayList<>(grades.entrySet());
        for (int i = 0; i < bands.size(); i++) {
            double low = bands.get(i).getValue();
            if (score < low) continue;
            double high = i == 0 ? Math.max(100.0, score) : bands.get(i - 1).getValue();
            String label = bands.get(i).getKey();
            if (high - low >= 3) {
                double within = (score - low) / (high - low);
                if (within >= 2.0 / 3) return label + "+";
                if (within < 1.0 / 3) return label + "-";
            }
            return label;
        }
        return "F";
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
