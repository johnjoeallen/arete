package net.dublinux.arete.web;

import net.dublinux.arete.scoring.spi.SpecScoringPlugin;
import net.dublinux.arete.web.api.Slugs;

import java.util.List;

/**
 * Policy (policy) names can contain spaces and mixed case ("Enterprise
 * Grade"). URLs and form fields carry a slug ("enterprise-grade"); this maps
 * between the two against a plugin's own {@link SpecScoringPlugin#getPolicies()}.
 */
public final class Policies {

    private Policies() {
    }

    public static String slug(String policyName) {
        String s = Slugs.slugify(policyName);
        return s != null ? s : policyName;
    }

    /**
     * Resolves a slug (or an exact name, or a legacy positional index) back to
     * the plugin's real policy name. Falls back to
     * {@link SpecScoringPlugin#DEFAULT_POLICY} for anything unrecognised.
     */
    public static String resolve(List<String> policyNames, String value) {
        if (value == null || value.isBlank() || policyNames.isEmpty()) {
            return SpecScoringPlugin.DEFAULT_POLICY;
        }
        String v = value.trim();
        for (String name : policyNames) {
            if (name.equalsIgnoreCase(v) || slug(name).equalsIgnoreCase(v)) {
                return name;
            }
        }
        try {
            int index = Integer.parseInt(v);
            if (index >= 0 && index < policyNames.size()) {
                return policyNames.get(index);
            }
        } catch (NumberFormatException ignored) {
            // not an index either
        }
        return SpecScoringPlugin.DEFAULT_POLICY;
    }
}
