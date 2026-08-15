package com.speculate.plugin;

import java.util.List;

/**
 * The combined outcome of running every enabled validator plugin against a
 * spec.
 *
 * <p>There is deliberately no single aggregate {@code Status} field. When
 * plugins disagree (e.g. one returns {@code SUCCESS} with violations while
 * another returns {@code PLUGIN_ERROR}), collapsing that onto one status
 * would force a choice between hiding the working plugin's clean results
 * behind the failing one's error, or hiding the failure behind the
 * successes — either way the UI loses information. Instead
 * {@link #pluginSummaries()} keeps every plugin's own outcome (including
 * its status and, for a failure, its error message) so each stays
 * independently visible and attributable, while {@link #violations()} is
 * the flattened, plugin-tagged list of findings from whichever plugins did
 * succeed. A single misbehaving plugin can't hide the others' results.
 */
public record AggregatedValidationResult(
        List<ValidationSummary> pluginSummaries,
        List<AttributedViolation> violations,
        int rulesEvaluatedCount,
        double overallScore,
        double overallScoreWithoutBlockers) {

    public boolean isEmpty() {
        return pluginSummaries.isEmpty();
    }
}
