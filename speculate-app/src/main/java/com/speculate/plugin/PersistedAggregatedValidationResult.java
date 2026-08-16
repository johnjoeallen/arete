package com.speculate.plugin;

import java.util.List;

/**
 * JSON-serializable mirror of {@link AggregatedValidationResult}, plus
 * {@code activePluginIds} — the plugin ids the run actually requested,
 * which {@link AggregatedValidationResult} itself has no field for (a
 * fully-compliant plugin reports zero violations, so it wouldn't otherwise
 * survive round-tripping through storage). {@code overallScore} /
 * {@code overallScoreWithoutBlockers} are {@code null} for {@link
 * Double#NaN} ("not computed") — see {@link ValidationResultSnapshotCodec}.
 */
public record PersistedAggregatedValidationResult(
        List<String> activePluginIds,
        List<PersistedValidationSummary> pluginSummaries,
        List<PersistedAttributedViolation> violations,
        int rulesEvaluatedCount,
        Double overallScore,
        Double overallScoreWithoutBlockers) {
}
