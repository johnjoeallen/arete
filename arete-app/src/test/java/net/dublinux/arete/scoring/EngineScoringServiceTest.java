package net.dublinux.arete.scoring;

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

class EngineScoringServiceTest {

    private final Engine engine = mock(Engine.class);
    private final EngineScoringService service = new EngineScoringService(engine);

    @Test
    void anUnknownEngineIdYieldsAnEmptyResult() {
        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", "nonexistent", null);

        assertThat(result.engineSummaries()).isEmpty();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void diagnosticsFromTheSelectedEngineAreTaggedWithItsId() {
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("no-empty-title").title("Title is empty").severity(net.dublinux.arete.engine.api.Severity.ERROR)
                .build();
        Engine scorer = stubEngine("Linter A");
        when(scorer.score(any())).thenReturn(ScoringResult.success(List.of(diagnostic), 10));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.diagnostics()).hasSize(1);
        AttributedDiagnostic attributed = result.diagnostics().get(0);
        assertThat(attributed.engineId()).isEqualTo(Engine.ID);
        assertThat(attributed.engineName()).isEqualTo("Linter A");
        assertThat(attributed.diagnostic()).isSameAs(diagnostic);
        assertThat(result.rulesEvaluatedCount()).isEqualTo(10);
    }

    @Test
    void aBlankPolicyFallsBackToTheDefaultPolicyConstant() {
        Engine scorer = stubEngine("A");
        when(scorer.score(any())).thenReturn(ScoringResult.success(List.of(), 1));

        service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(scorer).score(captor.capture());
        assertThat(captor.getValue().getPolicy()).isEqualTo(SpecInput.DEFAULT_POLICY);
    }

    @Test
    void anExplicitPolicyIsPassedThroughUnchanged() {
        Engine scorer = stubEngine("A");
        when(scorer.score(any())).thenReturn(ScoringResult.success(List.of(), 1));

        service.scoreOne("openapi: 3.0.0", Engine.ID, "lenient");

        org.mockito.ArgumentCaptor<SpecInput> captor = org.mockito.ArgumentCaptor.forClass(SpecInput.class);
        verify(scorer).score(captor.capture());
        assertThat(captor.getValue().getPolicy()).isEqualTo("lenient");
    }

    @Test
    void rulesEvaluatedCountIsUnknownWhenTheEngineDoesNotReportIt() {
        Engine scorer = stubEngine("A");
        when(scorer.score(any())).thenReturn(ScoringResult.success(List.of(), -1));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.rulesEvaluatedCount()).isEqualTo(-1);
    }

    @Test
    void aThrowingEngineYieldsAnEngineErrorSummaryInsteadOfPropagating() {
        Engine failing = stubEngine("Broken Engine");
        when(failing.score(any())).thenThrow(new RuntimeException("boom"));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, null);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.engineSummaries()).hasSize(1);
        assertThat(result.engineSummaries().get(0).engineName()).isEqualTo("Broken Engine");
        assertThat(result.engineSummaries().get(0).status()).isEqualTo("ENGINE_ERROR");
    }

    @Test
    void anEngineGradeAndPassingScoreAreCarriedOntoTheAggregatedResult() {
        Engine scorer = stubEngine("Graded");
        when(scorer.score(any())).thenReturn(ScoringResult.builder()
                .status(ScoringResult.Status.SUCCESS).diagnostics(List.of())
                .overallScore(92.5).overallScoreWithoutBlockers(92.5).grade("B").build());
        when(scorer.getPassingScore(any())).thenReturn(java.util.OptionalDouble.of(90.0));

        AggregatedScoringResult result = service.scoreOne("openapi: 3.0.0", Engine.ID, "Enterprise Grade");

        assertThat(result.grade()).isEqualTo("B");           // a passing score still carries its grade
        assertThat(result.passingScore()).isEqualTo(90.0);
        assertThat(result.meetsPassingScore()).isTrue();
    }

    /** The service holds one engine, so every test scores through it; each stubs what it needs. */
    private Engine stubEngine(String name) {
        when(engine.getId()).thenReturn(Engine.ID);
        when(engine.getName()).thenReturn(name);
        return engine;
    }
}
