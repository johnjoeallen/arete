package net.dublinux.arete.scoring;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * JSON-serializable mirror of {@link AggregatedScoringResult}, plus
 * {@code activeEngineIds} — the engine ids the run actually requested,
 * which {@link AggregatedScoringResult} itself has no field for (a
 * fully-compliant engine reports zero diagnostics, so it wouldn't otherwise
 * survive round-tripping through storage). {@code overallScore} /
 * {@code overallScoreWithoutBlockers} are {@code null} for {@link
 * Double#NaN} ("not computed") — see {@link ScoringResultSnapshotCodec}.
 */
public record PersistedAggregatedScoringResult(
        @JsonAlias("activePluginIds") List<String> activeEngineIds,
        @JsonAlias("pluginSummaries") List<PersistedScoringSummary> engineSummaries,
        List<PersistedAttributedDiagnostic> diagnostics,
        int rulesEvaluatedCount,
        Double overallScore,
        Double overallScoreWithoutBlockers,
        String grade,
        Double passingScore) {
}
