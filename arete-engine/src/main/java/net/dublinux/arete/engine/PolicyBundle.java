package net.dublinux.arete.engine;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

record PolicyBundle(Map<String, PolicyRule> rules, Map<String, Policy> policies, Map<String, Matcher> matchers,
        String bundleId, String bundleVersion) {
    PolicyBundle {
        matchers = Collections.unmodifiableMap(new LinkedHashMap<>(matchers));
        policies = Collections.unmodifiableMap(new LinkedHashMap<>(policies));
        rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    PolicyBundle(Map<String, PolicyRule> rules, Map<String, Policy> policies, Map<String, Matcher> matchers) {
        this(rules, policies, matchers, null, null);
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
        Double passingScore, Map<String, Double> grades, Set<String> locked) {
    Policy {
        locked = locked == null ? Set.of() : Set.copyOf(locked);
        // Policy declaration order is report order. Map.copyOf deliberately
        // makes no iteration-order promise, so retain the YAML LinkedHashMap.
        dispositions = Collections.unmodifiableMap(new LinkedHashMap<>(dispositions));
        grades = grades == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(grades));
    }

    Policy(String id, Map<String, PolicyDisposition> dispositions) {
        this(id, dispositions, null, null, Map.of(), Set.of());
    }

    Policy(String id, Map<String, PolicyDisposition> dispositions, String scoreLevel, Double passingScore, Map<String, Double> grades) {
        this(id, dispositions, scoreLevel, passingScore, grades, Set.of());
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
/**
 * What a policy does with a rule. {@code expectMatch} flips the rule: instead of a match being the violation, finding
 * nothing is (the matcher looks for something that should be there).
 */
sealed interface PolicyDisposition permits Deduction, Prohibited, Graduated {
    Map<String, Object> parameters();
    boolean expectMatch();
    PolicyDisposition withParameters(Map<String, Object> parameters);
}

/** A flat cost, charged once however many times the rule matches. */
record Deduction(double points, Map<String, Object> parameters, boolean expectMatch) implements PolicyDisposition {
    Deduction {
        parameters = Map.copyOf(parameters);
    }
    Deduction(double points, Map<String, Object> parameters) { this(points, parameters, false); }
    Deduction(double points) { this(points, Map.of(), false); }
    @Override public Deduction withParameters(Map<String, Object> changed) { return new Deduction(points, changed, expectMatch); }
}

/** Any violation forces the score to 0. */
record Prohibited(Map<String, Object> parameters, boolean expectMatch) implements PolicyDisposition {
    Prohibited {
        parameters = Map.copyOf(parameters);
    }
    Prohibited(Map<String, Object> parameters) { this(parameters, false); }
    Prohibited() { this(Map.of(), false); }
    @Override public Prohibited withParameters(Map<String, Object> changed) { return new Prohibited(changed, expectMatch); }
}

/**
 * A cost that depends on how many times the rule matches: {@code perMatch} points each up to {@code max}, or the
 * points of the highest {@link Tier} the count reaches (below the lowest tier the rule is not violated at all).
 */
record Graduated(double perMatch, Double max, List<Tier> tiers, Map<String, Object> parameters, boolean expectMatch, boolean byValue) implements PolicyDisposition {
    Graduated {
        tiers = List.copyOf(tiers);
        parameters = Map.copyOf(parameters);
    }

    Graduated(double perMatch, Double max, List<Tier> tiers, Map<String, Object> parameters, boolean expectMatch) {
        this(perMatch, max, tiers, parameters, expectMatch, false);
    }

    /** True when the rule is scored by tier and the measure is below the first one, so it is not a violation yet. */
    boolean violatedAt(double measure) {
        return tiers.isEmpty() ? measure > 0 : measure >= tiers.get(0).minimum();
    }

    /** The cost of a measure: the match count, or (when {@link #byValue()}) the largest value the matcher reported. */
    double costAt(double measure) {
        if (!tiers.isEmpty()) {
            double points = 0;
            for (Tier tier : tiers) if (measure >= tier.minimum()) points = tier.points();
            return points;
        }
        double raw = perMatch * measure;
        return max == null ? raw : Math.min(max, raw);
    }

    @Override public Graduated withParameters(Map<String, Object> changed) { return new Graduated(perMatch, max, tiers, changed, expectMatch, byValue); }
}

/** At {@code minimum} matches or more, the rule costs {@code points}. */
record Tier(int minimum, double points) { }
/** A matcher's occurrence: where, about what, saying what, and (optionally) a measured value a policy may charge by. */
record Diagnostic(String pointer, String path, String message, Double value) {
    Diagnostic(String pointer, String path, String message) { this(pointer, path, message, null); }
}

final class MatcherEvaluationException extends RuntimeException {
    MatcherEvaluationException(String message) { super(message); }
    MatcherEvaluationException(String message, Throwable cause) { super(message, cause); }
}
