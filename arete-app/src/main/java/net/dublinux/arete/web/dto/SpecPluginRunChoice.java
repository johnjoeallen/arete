package net.dublinux.arete.web.dto;

import java.util.List;

/**
 * One globally-enabled plugin as it renders in a spec view's picker: its rule
 * sets (each with a URL-safe slug), whether it's enabled for this spec, and
 * which policy is selected (by slug — the form submits {@code policy_<pluginId>}
 * = slug, not a positional index).
 */
public record SpecPluginRunChoice(String pluginId, String pluginName, List<Policy> policies,
        boolean enabled, String selectedSlug) {

    public record Policy(String name, String slug) {
    }
}
