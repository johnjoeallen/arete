package com.speculate.plugin;

import org.junit.jupiter.api.Test;
import speculate.validation.spi.SpecFormat;
import speculate.validation.spi.SpecValidationPlugin;
import speculate.validation.spi.ValidationResult;
import speculate.validation.spi.Violation;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginValidationServiceTest {

    private final PluginRegistry pluginRegistry = mock(PluginRegistry.class);
    private final PluginSettingsService pluginSettingsService = mock(PluginSettingsService.class);
    private final PluginValidationService service =
            new PluginValidationService(pluginRegistry, pluginSettingsService);

    @Test
    void disabledPluginsAreNeverInvoked() {
        SpecValidationPlugin disabled = stubPlugin("off", "Disabled Plugin");
        when(pluginRegistry.getPlugins()).thenReturn(List.of(disabled));
        when(pluginSettingsService.isEnabled("off")).thenReturn(false);

        AggregatedValidationResult result = service.validate("openapi: 3.0.0");

        verify(disabled, never()).validate(any());
        assertThat(result.pluginSummaries()).isEmpty();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void violationsFromEnabledPluginsAreTaggedWithTheirSourcePlugin() {
        Violation violation = Violation.builder()
                .ruleId("no-empty-title").title("Title is empty").severity(speculate.validation.spi.Severity.ERROR)
                .build();
        SpecValidationPlugin plugin = stubPlugin("linter-a", "Linter A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(violation), 10));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("linter-a")).thenReturn(true);

        AggregatedValidationResult result = service.validate("openapi: 3.0.0");

        assertThat(result.violations()).hasSize(1);
        AttributedViolation attributed = result.violations().get(0);
        assertThat(attributed.pluginId()).isEqualTo("linter-a");
        assertThat(attributed.pluginName()).isEqualTo("Linter A");
        assertThat(attributed.violation()).isSameAs(violation);
    }

    @Test
    void rulesEvaluatedCountSumsAcrossPluginsAndIgnoresUnknownCounts() {
        SpecValidationPlugin knownCount = stubPlugin("a", "A");
        when(knownCount.validate(any())).thenReturn(ValidationResult.success(List.of(), 5));
        SpecValidationPlugin unknownCount = stubPlugin("b", "B");
        when(unknownCount.validate(any())).thenReturn(ValidationResult.success(List.of(), -1));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(knownCount, unknownCount));
        when(pluginSettingsService.isEnabled("a")).thenReturn(true);
        when(pluginSettingsService.isEnabled("b")).thenReturn(true);

        AggregatedValidationResult result = service.validate("openapi: 3.0.0");

        assertThat(result.rulesEvaluatedCount()).isEqualTo(5);
    }

    @Test
    void rulesEvaluatedCountIsUnknownWhenNoPluginReportsIt() {
        SpecValidationPlugin plugin = stubPlugin("a", "A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(), -1));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("a")).thenReturn(true);

        AggregatedValidationResult result = service.validate("openapi: 3.0.0");

        assertThat(result.rulesEvaluatedCount()).isEqualTo(-1);
    }

    @Test
    void aFailingPluginDoesNotHideAnotherPluginsCleanResults() {
        SpecValidationPlugin failing = stubPlugin("broken", "Broken Plugin");
        when(failing.validate(any())).thenThrow(new RuntimeException("boom"));
        Violation violation = Violation.builder()
                .ruleId("ok-rule").title("Fine").severity(speculate.validation.spi.Severity.INFO)
                .build();
        SpecValidationPlugin working = stubPlugin("working", "Working Plugin");
        when(working.validate(any())).thenReturn(ValidationResult.success(List.of(violation), 3));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(failing, working));
        when(pluginSettingsService.isEnabled("broken")).thenReturn(true);
        when(pluginSettingsService.isEnabled("working")).thenReturn(true);

        AggregatedValidationResult result = service.validate("openapi: 3.0.0");

        assertThat(result.violations()).hasSize(1);
        assertThat(result.pluginSummaries()).hasSize(2);
        assertThat(result.pluginSummaries()).anySatisfy(s -> {
            assertThat(s.pluginName()).isEqualTo("Broken Plugin");
            assertThat(s.status()).isEqualTo("PLUGIN_ERROR");
        });
        assertThat(result.pluginSummaries()).anySatisfy(s -> {
            assertThat(s.pluginName()).isEqualTo("Working Plugin");
            assertThat(s.status()).isEqualTo("SUCCESS");
            assertThat(s.violationCount()).isEqualTo(1);
        });
    }

    private static SpecValidationPlugin stubPlugin(String id, String name) {
        SpecValidationPlugin plugin = mock(SpecValidationPlugin.class);
        when(plugin.getId()).thenReturn(id);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getSupportedFormats()).thenReturn(Set.of(SpecFormat.OPENAPI3));
        return plugin;
    }
}
