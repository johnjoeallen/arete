package com.speculate.web.dto;

import java.util.List;

/**
 * One enabled plugin's set of named rule sets, for the "add a spec" forms
 * to render as a picker. Only plugins that actually declare more than one
 * set appear here — a single-set plugin has no real choice to offer.
 */
public record PluginRuleSetChoice(String pluginId, String pluginName, List<String> ruleSets) {
}
