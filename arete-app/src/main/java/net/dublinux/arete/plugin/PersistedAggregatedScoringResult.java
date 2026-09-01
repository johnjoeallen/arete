package net.dublinux.arete.plugin;

import java.util.List;

/**
 * JSON-serializable mirror of {@link AggregatedScoringResult}, plus
 * {@code activePluginIds} — the plugin ids the run actually requested,
 * which {@link AggregatedScoringResult} itself has no field for (a
 * fully-compliant plugin reports zero diagnostics, so it wouldn't otherwise
 * survive round-tripping through storage). {@code overallScore} /
 * {@code overallScoreWithoutBlockers} are {@code null} for {@link
 * Double#NaN} ("not computed") — see {@link ScoringResultSnapshotCodec}.
 */
public record PersistedAggregatedScoringResult(
        List<String> activePluginIds,
        List<PersistedScoringSummary> pluginSummaries,
        List<PersistedAttributedDiagnostic> diagnostics,
        int rulesEvaluatedCount,
        Double overallScore,
        Double overallScoreWithoutBlockers,
        String grade,
        Double passingScore) {
}
