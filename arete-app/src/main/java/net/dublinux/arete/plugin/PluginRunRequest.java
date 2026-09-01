package net.dublinux.arete.plugin;

/**
 * One plugin to run, with the rule set to run it under. {@code ruleSet}
 * follows {@link net.dublinux.arete.scoring.spi.SpecScoringPlugin#DEFAULT_RULE_SET}'s
 * own null/blank-means-default convention — see {@link PluginScoringService#scoreMany}.
 */
public record PluginRunRequest(String pluginId, String ruleSet) {
}
