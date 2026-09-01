package net.dublinux.arete.plugin;

/**
 * One plugin to run, with the policy to run it under. {@code policy}
 * follows {@link net.dublinux.arete.scoring.spi.SpecScoringPlugin#DEFAULT_POLICY}'s
 * own null/blank-means-default convention — see {@link PluginScoringService#scoreMany}.
 */
public record PluginRunRequest(String pluginId, String policy) {
}
