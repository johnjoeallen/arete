package net.dublinux.arete.web.dto;

import java.util.List;

/**
 * One globally-enabled plugin as it should render in a spec view page's
 * plugin picker: its declared rule sets, whether it's currently
 * enabled <em>for this spec</em> (the checkbox state), and which rule set
 * is currently selected (an index into {@code ruleSets}, the same
 * position-not-name convention the rule-set picker has always used).
 */
public record SpecPluginRunChoice(String pluginId, String pluginName, List<String> ruleSets, boolean enabled, int selectedRuleSetIndex) {
}
