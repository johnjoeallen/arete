package com.speculate.web.dto;

import java.util.List;

/**
 * One enabled plugin and its declared rule sets, for the spec view page's
 * plugin/rule-set picker — every enabled plugin appears here, even one
 * with only the implicit default set (its {@code ruleSets} just has one
 * entry).
 */
public record PluginRuleSetChoice(String pluginId, String pluginName, List<String> ruleSets) {
}
