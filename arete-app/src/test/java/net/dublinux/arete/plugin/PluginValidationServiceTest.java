package net.dublinux.arete.plugin;

import org.junit.jupiter.api.Test;
import net.dublinux.arete.validation.spi.SpecFormat;
import net.dublinux.arete.validation.spi.SpecInput;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import net.dublinux.arete.validation.spi.ValidationResult;
import net.dublinux.arete.validation.spi.Diagnostic;

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
    void aDisabledPluginIsNeverInvokedAndYieldsAnEmptyResult() {
        SpecValidationPlugin disabled = stubPlugin("off", "Disabled Plugin");
        when(pluginRegistry.getPlugins()).thenReturn(List.of(disabled));
        when(pluginSettingsService.isEnabled("off")).thenReturn(false);

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "off", null);

        verify(disabled, never()).validate(any());
        assertThat(result.pluginSummaries()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void anUnknownPluginIdYieldsAnEmptyResult() {
        when(pluginRegistry.getPlugins()).thenReturn(List.of());

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "nonexistent", null);

        assertThat(result.pluginSummaries()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void diagnosticsFromTheSelectedPluginAreTaggedWithItsId() {
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("no-empty-title").title("Title is empty").severity(net.dublinux.arete.validation.spi.Severity.ERROR)
                .build();
        SpecValidationPlugin plugin = stubPlugin("linter-a", "Linter A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(diagnostic), 10));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("linter-a")).thenReturn(true);

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "linter-a", null);

        assertThat(result.diagnostics()).hasSize(1);
        AttributedDiagnostic attributed = result.diagnostics().get(0);
        assertThat(attributed.pluginId()).isEqualTo("linter-a");
        assertThat(attributed.pluginName()).isEqualTo("Linter A");
        assertThat(attributed.diagnostic()).isSameAs(diagnostic);
        assertThat(result.rulesEvaluatedCount()).isEqualTo(10);
    }

    @Test
    void aBlankRuleSetFallsBackToTheDefaultRuleSetConstant() {
        SpecValidationPlugin plugin = stubPlugin("a", "A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(), 1));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("a")).thenReturn(true);

        service.validateOne("openapi: 3.0.0", "a", null);

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(plugin).validate(captor.capture());
        assertThat(captor.getValue().getRuleSet()).isEqualTo(SpecValidationPlugin.DEFAULT_RULE_SET);
    }

    @Test
    void anExplicitRuleSetIsPassedThroughUnchanged() {
        SpecValidationPlugin plugin = stubPlugin("a", "A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(), 1));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("a")).thenReturn(true);

        service.validateOne("openapi: 3.0.0", "a", "lenient");

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(plugin).validate(captor.capture());
        assertThat(captor.getValue().getRuleSet()).isEqualTo("lenient");
    }

    @Test
    void rulesEvaluatedCountIsUnknownWhenThePluginDoesNotReportIt() {
        SpecValidationPlugin plugin = stubPlugin("a", "A");
        when(plugin.validate(any())).thenReturn(ValidationResult.success(List.of(), -1));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("a")).thenReturn(true);

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "a", null);

        assertThat(result.rulesEvaluatedCount()).isEqualTo(-1);
    }

    @Test
    void aThrowingPluginYieldsAPluginErrorSummaryInsteadOfPropagating() {
        SpecValidationPlugin failing = stubPlugin("broken", "Broken Plugin");
        when(failing.validate(any())).thenThrow(new RuntimeException("boom"));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(failing));
        when(pluginSettingsService.isEnabled("broken")).thenReturn(true);

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "broken", null);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.pluginSummaries()).hasSize(1);
        assertThat(result.pluginSummaries().get(0).pluginName()).isEqualTo("Broken Plugin");
        assertThat(result.pluginSummaries().get(0).status()).isEqualTo("PLUGIN_ERROR");
    }

    @Test
    void aPluginGradeAndPassingScoreAreCarriedOntoTheAggregatedResult() {
        SpecValidationPlugin plugin = stubPlugin("g", "Graded");
        when(plugin.validate(any())).thenReturn(ValidationResult.builder()
                .status(ValidationResult.Status.SUCCESS).diagnostics(List.of())
                .overallScore(92.5).overallScoreWithoutBlockers(92.5).grade("B").build());
        when(plugin.getPassingScore(any())).thenReturn(java.util.OptionalDouble.of(90.0));
        when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        when(pluginSettingsService.isEnabled("g")).thenReturn(true);

        AggregatedValidationResult result = service.validateOne("openapi: 3.0.0", "g", "Enterprise Grade");

        assertThat(result.grade()).isEqualTo("B");           // a passing score still carries its grade
        assertThat(result.passingScore()).isEqualTo(90.0);
        assertThat(result.meetsPassingScore()).isTrue();
    }

    private static SpecValidationPlugin stubPlugin(String id, String name) {
        SpecValidationPlugin plugin = mock(SpecValidationPlugin.class);
        when(plugin.getId()).thenReturn(id);
        when(plugin.getName()).thenReturn(name);
        when(plugin.getSupportedFormats()).thenReturn(Set.of(SpecFormat.OPENAPI3));
        return plugin;
    }
}
