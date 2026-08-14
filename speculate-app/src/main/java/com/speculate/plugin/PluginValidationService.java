package com.speculate.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
import net.dublinux.speculate.validation.spi.ValidationResult;
import net.dublinux.speculate.validation.spi.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a single, caller-chosen <em>enabled</em> {@link SpecValidationPlugin}
 * against a raw spec. Validation is on-demand and single-plugin by design:
 * the host never runs anything automatically, and never aggregates more
 * than one plugin's results into a single view — the caller (the spec view
 * page's Refresh control) picks exactly one plugin and rule set per run.
 * Disabled plugins are never selectable in the first place (see
 * {@link PluginRegistry}), but this still refuses to run one defensively.
 */
@Service
public class PluginValidationService {

    private static final Logger log = LoggerFactory.getLogger(PluginValidationService.class);

    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettingsService;

    public PluginValidationService(PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService) {
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
    }

    /**
     * @param pluginId the {@link SpecValidationPlugin#getId()} to run; if
     *                  it isn't loaded or isn't enabled, the result is empty
     *                  (no summaries, no violations) rather than an error —
     *                  there's nothing meaningful to report about a plugin
     *                  the caller couldn't legitimately have selected
     * @param ruleSet   one of that plugin's {@link SpecValidationPlugin#getRuleSets()}
     *                  values, or {@link SpecValidationPlugin#DEFAULT_RULE_SET}
     */
    public AggregatedValidationResult validateOne(String rawSpec, String pluginId, String ruleSet) {
        SpecValidationPlugin plugin = findEnabled(pluginId);
        if (plugin == null) {
            return new AggregatedValidationResult(List.of(), List.of(), -1);
        }

        SpecInput input = SpecInput.builder()
                .content(rawSpec)
                .format(detectFormat(rawSpec))
                .ruleSet(ruleSet == null || ruleSet.isBlank() ? SpecValidationPlugin.DEFAULT_RULE_SET : ruleSet)
                .build();
        ValidationResult result = runOne(plugin, input);
        ValidationSummary summary = toSummary(plugin, result);

        List<AttributedViolation> violations = new ArrayList<>();
        int rulesEvaluatedCount = -1;
        if (result.getStatus() == ValidationResult.Status.SUCCESS) {
            for (Violation violation : result.getViolations()) {
                violations.add(new AttributedViolation(plugin.getId(), plugin.getName(), violation));
            }
            rulesEvaluatedCount = result.getRulesEvaluatedCount();
        }

        return new AggregatedValidationResult(List.of(summary), violations, rulesEvaluatedCount);
    }

    private SpecValidationPlugin findEnabled(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (plugin.getId().equals(pluginId) && pluginSettingsService.isEnabled(plugin.getId())) {
                return plugin;
            }
        }
        return null;
    }

    private static ValidationResult runOne(SpecValidationPlugin plugin, SpecInput input) {
        try {
            return plugin.validate(input);
        } catch (Throwable t) {
            // Defensive backstop per the interface's documented contract: a plugin
            // must never be able to break a validation run for the whole host.
            log.warn("Validation plugin '{}' threw unexpectedly: {}", plugin.getId(), t.toString());
            return ValidationResult.pluginError(t.toString());
        }
    }

    private static ValidationSummary toSummary(SpecValidationPlugin plugin, ValidationResult result) {
        return switch (result.getStatus()) {
            case SUCCESS -> new ValidationSummary(
                    plugin.getName(), "SUCCESS", result.getViolations().size(), null);
            case PARSE_ERROR, PLUGIN_ERROR -> new ValidationSummary(
                    plugin.getName(), result.getStatus().name(), 0, result.getErrorMessage());
        };
    }

    private static SpecFormat detectFormat(String rawSpec) {
        if (rawSpec.contains("\"swagger\"") || rawSpec.matches("(?s).*(^|\\n)\\s*swagger\\s*:.*")) {
            return SpecFormat.SWAGGER2;
        }
        return SpecFormat.OPENAPI3;
    }
}
