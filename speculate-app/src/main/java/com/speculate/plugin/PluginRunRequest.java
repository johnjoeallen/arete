package com.speculate.plugin;

/**
 * One plugin to run, with the rule set to run it under. {@code ruleSet}
 * follows {@link net.dublinux.speculate.validation.spi.SpecValidationPlugin#DEFAULT_RULE_SET}'s
 * own null/blank-means-default convention — see {@link PluginValidationService#validateMany}.
 */
public record PluginRunRequest(String pluginId, String ruleSet) {
}
