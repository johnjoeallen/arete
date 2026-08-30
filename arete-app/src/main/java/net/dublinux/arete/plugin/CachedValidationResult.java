package net.dublinux.arete.plugin;

import java.util.List;

/** A previously-persisted Score run's result plus which plugin ids it actually requested — see {@link PersistedAggregatedValidationResult}. */
public record CachedValidationResult(AggregatedValidationResult result, List<String> activePluginIds) {
}
