package net.dublinux.arete.scoring;

import java.util.List;

/** A previously-persisted Score run's result plus which engine ids it actually requested — see {@link PersistedAggregatedScoringResult}. */
public record CachedScoringResult(AggregatedScoringResult result, List<String> activeEngineIds) {
}
