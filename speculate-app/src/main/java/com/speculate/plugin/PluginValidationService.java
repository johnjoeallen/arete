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
 * Runs every <em>enabled</em> {@link SpecValidationPlugin} against a raw
 * spec and combines their outcomes into one {@link AggregatedValidationResult}.
 * Disabled plugins stay loaded (see {@link PluginRegistry}) but are skipped
 * here entirely — not even {@code validate()} is called on them.
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

    public AggregatedValidationResult validate(String rawSpec) {
        SpecInput input = SpecInput.builder()
                .content(rawSpec)
                .format(detectFormat(rawSpec))
                .build();

        List<ValidationSummary> summaries = new ArrayList<>();
        List<AttributedViolation> violations = new ArrayList<>();
        int rulesEvaluatedTotal = 0;
        boolean anyRulesEvaluatedCountKnown = false;

        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (!pluginSettingsService.isEnabled(plugin.getId())) {
                continue;
            }

            ValidationResult result = runOne(plugin, input);
            summaries.add(toSummary(plugin, result));

            if (result.getStatus() == ValidationResult.Status.SUCCESS) {
                for (Violation violation : result.getViolations()) {
                    violations.add(new AttributedViolation(plugin.getId(), plugin.getName(), violation));
                }
                // -1 means "unknown / not reported" per the SPI contract; a plugin
                // that can't produce this number must not be allowed to corrupt the
                // aggregate sum, so it's skipped rather than treated as zero.
                if (result.getRulesEvaluatedCount() >= 0) {
                    rulesEvaluatedTotal += result.getRulesEvaluatedCount();
                    anyRulesEvaluatedCountKnown = true;
                }
            }
        }

        return new AggregatedValidationResult(
                summaries, violations, anyRulesEvaluatedCountKnown ? rulesEvaluatedTotal : -1);
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
