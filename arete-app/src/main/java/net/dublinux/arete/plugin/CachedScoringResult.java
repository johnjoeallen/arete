package net.dublinux.arete.plugin;

import java.util.List;

/** A previously-persisted Score run's result plus which plugin ids it actually requested — see {@link PersistedAggregatedScoringResult}. */
public record CachedScoringResult(AggregatedScoringResult result, List<String> activePluginIds) {
}
