package net.dublinux.arete.web.dto;

import java.util.List;

/**
 * One globally-enabled plugin as it renders in a spec view's picker: its rule
 * sets (each with a URL-safe slug), whether it's enabled for this spec, and
 * which rule set is selected (by slug — the form submits {@code ruleSet_<pluginId>}
 * = slug, not a positional index).
 */
public record SpecPluginRunChoice(String pluginId, String pluginName, List<RuleSet> ruleSets,
        boolean enabled, String selectedSlug) {

    public record RuleSet(String name, String slug) {
    }
}
