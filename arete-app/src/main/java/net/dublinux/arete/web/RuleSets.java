package net.dublinux.arete.web;

import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import net.dublinux.arete.web.api.Slugs;

import java.util.List;

/**
 * Rule-set (policy) names can contain spaces and mixed case ("Enterprise
 * Grade"). URLs and form fields carry a slug ("enterprise-grade"); this maps
 * between the two against a plugin's own {@link SpecValidationPlugin#getRuleSets()}.
 */
public final class RuleSets {

    private RuleSets() {
    }

    public static String slug(String ruleSetName) {
        String s = Slugs.slugify(ruleSetName);
        return s != null ? s : ruleSetName;
    }

    /**
     * Resolves a slug (or an exact name, or a legacy positional index) back to
     * the plugin's real rule-set name. Falls back to
     * {@link SpecValidationPlugin#DEFAULT_RULE_SET} for anything unrecognised.
     */
    public static String resolve(List<String> ruleSetNames, String value) {
        if (value == null || value.isBlank() || ruleSetNames.isEmpty()) {
            return SpecValidationPlugin.DEFAULT_RULE_SET;
        }
        String v = value.trim();
        for (String name : ruleSetNames) {
            if (name.equalsIgnoreCase(v) || slug(name).equalsIgnoreCase(v)) {
                return name;
            }
        }
        try {
            int index = Integer.parseInt(v);
            if (index >= 0 && index < ruleSetNames.size()) {
                return ruleSetNames.get(index);
            }
        } catch (NumberFormatException ignored) {
            // not an index either
        }
        return SpecValidationPlugin.DEFAULT_RULE_SET;
    }
}
