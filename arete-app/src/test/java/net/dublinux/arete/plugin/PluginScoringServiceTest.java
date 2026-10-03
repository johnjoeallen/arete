package net.dublinux.arete.plugin;

import org.junit.jupiter.api.Test;
import net.dublinux.arete.engine.api.SpecInput;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.Diagnostic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginScoringServiceTest {

    private final Engine engine = mock(Engine.class);
    private final PluginScoringService service = new PluginScoringService(engine);

    @Test
    void anUnknownPluginIdYieldsAnEmptyResult() {
        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", "nonexistent", null);

        assertThat(result.pluginSummaries()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void diagnosticsFromTheSelectedPluginAreTaggedWithItsId() {
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("no-empty-title").title("Title is empty").severity(net.dublinux.arete.engine.api.Severity.ERROR)
                .build();
        Engine plugin = stubPlugin("Linter A");
        when(plugin.score(any())).thenReturn(ScoringResult.success(List.of(diagnostic), 10));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.diagnostics()).hasSize(1);
        AttributedDiagnostic attributed = result.diagnostics().get(0);
        assertThat(attributed.pluginId()).isEqualTo(Engine.ID);
        assertThat(attributed.pluginName()).isEqualTo("Linter A");
        assertThat(attributed.diagnostic()).isSameAs(diagnostic);
        assertThat(result.rulesEvaluatedCount()).isEqualTo(10);
    }

    @Test
    void aBlankPolicyFallsBackToTheDefaultPolicyConstant() {
        Engine plugin = stubPlugin("A");
        when(plugin.score(any())).thenReturn(ScoringResult.success(List.of(), 1));

        service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(plugin).score(captor.capture());
        assertThat(captor.getValue().getPolicy()).isEqualTo(SpecInput.DEFAULT_POLICY);
    }

    @Test
    void anExplicitPolicyIsPassedThroughUnchanged() {
        Engine plugin = stubPlugin("A");
        when(plugin.score(any())).thenReturn(ScoringResult.success(List.of(), 1));

        service.scoreOne("openapi: 3.0.0", Engine.ID, "lenient");

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(plugin).score(captor.capture());
        assertThat(captor.getValue().getPolicy()).isEqualTo("lenient");
    }

    @Test
    void rulesEvaluatedCountIsUnknownWhenThePluginDoesNotReportIt() {
        Engine plugin = stubPlugin("A");
        when(plugin.score(any())).thenReturn(ScoringResult.success(List.of(), -1));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.rulesEvaluatedCount()).isEqualTo(-1);
    }

    @Test
    void aThrowingPluginYieldsAPluginErrorSummaryInsteadOfPropagating() {
        Engine failing = stubPlugin("Broken Plugin");
        when(failing.score(any())).thenThrow(new RuntimeException("boom"));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.pluginSummaries()).hasSize(1);
        assertThat(result.pluginSummaries().get(0).pluginName()).isEqualTo("Broken Plugin");
        assertThat(result.pluginSummaries().get(0).status()).isEqualTo("PLUGIN_ERROR");
    }

    @Test
    void aPluginGradeAndPassingScoreAreCarriedOntoTheAggregatedResult() {
        Engine plugin = stubPlugin("Graded");
        when(plugin.score(any())).thenReturn(ScoringResult.builder()
                .status(ScoringResult.Status.SUCCESS).diagnostics(List.of())
                .overallScore(92.5).overallScoreWithoutBlockers(92.5).grade("B").build());
        when(plugin.getPassingScore(any())).thenReturn(java.util.OptionalDouble.of(90.0));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, "Enterprise Grade");

        assertThat(result.grade()).isEqualTo("B");           // a passing score still carries its grade
        assertThat(result.passingScore()).isEqualTo(90.0);
        assertThat(result.meetsPassingScore()).isTrue();
    }

    /** The service holds one engine, so every test scores through it; each stubs what it needs. */
    private Engine stubPlugin(String name) {
        when(engine.getId()).thenReturn(Engine.ID);
        when(engine.getName()).thenReturn(name);
        return engine;
    }
}
