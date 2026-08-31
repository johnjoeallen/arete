package net.dublinux.arete.plugin;

import net.dublinux.arete.validation.spi.Severity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The combined outcome of running every enabled validator plugin against a
 * spec.
 *
 * <p>There is deliberately no single aggregate {@code Status} field. When
 * plugins disagree (e.g. one returns {@code SUCCESS} with diagnostics while
 * another returns {@code PLUGIN_ERROR}), collapsing that onto one status
 * would force a choice between hiding the working plugin's clean results
 * behind the failing one's error, or hiding the failure behind the
 * successes — either way the UI loses information. Instead
 * {@link #pluginSummaries()} keeps every plugin's own outcome (including
 * its status and, for a failure, its error message) so each stays
 * independently visible and attributable, while {@link #diagnostics()} is
 * the flattened, plugin-tagged list of findings from whichever plugins did
 * succeed. A single misbehaving plugin can't hide the others' results.
 */
public record AggregatedValidationResult(
        List<ValidationSummary> pluginSummaries,
        List<AttributedDiagnostic> diagnostics,
        int rulesEvaluatedCount,
        double overallScore,
        double overallScoreWithoutBlockers,
        String grade,
        double passingScore) {

    /** Kept for callers that predate grade/passing-score. */
    public AggregatedValidationResult(List<ValidationSummary> pluginSummaries, List<AttributedDiagnostic> diagnostics,
            int rulesEvaluatedCount, double overallScore, double overallScoreWithoutBlockers) {
        this(pluginSummaries, diagnostics, rulesEvaluatedCount, overallScore, overallScoreWithoutBlockers, null, Double.NaN);
    }

    public boolean isEmpty() {
        return pluginSummaries.isEmpty();
    }

    /** True when a passing score is defined and the overall score meets it. */
    public boolean meetsPassingScore() {
        return !Double.isNaN(passingScore) && !Double.isNaN(overallScore) && overallScore >= passingScore;
    }

    /**
     * How many score points each {@link Severity} is currently costing —
     * i.e. the sum of {@link net.dublinux.arete.validation.spi.Diagnostic#getScoreImprovement()}
     * across that severity's diagnostics, deduped by rule id first (since
     * that value is per-rule, not per-finding — see that method's javadoc).
     * A severity with no diagnostics, or whose diagnostics never report a
     * score, is simply absent from the map rather than present with a
     * {@code 0}/{@code NaN} entry — the host must treat "absent" as "not
     * computed" the same way it treats {@link Double#NaN} elsewhere.
     */
    public Map<Severity, Double> severityScoreImpact() {
        Map<Severity, Map<String, Double>> impactByRulePerSeverity = new LinkedHashMap<>();
        for (AttributedDiagnostic av : diagnostics) {
            double improvement = av.diagnostic().getScoreImprovement();
            if (Double.isNaN(improvement)) {
                continue;
            }
            impactByRulePerSeverity
                    .computeIfAbsent(av.diagnostic().getSeverity(), s -> new LinkedHashMap<>())
                    .put(av.diagnostic().getRuleId(), improvement);
        }
        Map<Severity, Double> impactBySeverity = new LinkedHashMap<>();
        for (Map.Entry<Severity, Map<String, Double>> entry : impactByRulePerSeverity.entrySet()) {
            impactBySeverity.put(entry.getKey(), entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum());
        }
        return impactBySeverity;
    }
}
