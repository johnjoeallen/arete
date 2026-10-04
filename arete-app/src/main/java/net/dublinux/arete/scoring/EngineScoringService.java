package net.dublinux.arete.scoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.Diagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the {@link Engine} against a raw spec. Scoring is on-demand: the
 * application never runs anything automatically, the caller (the spec view
 * page's Refresh control, or the automation API) picks the policy per run.
 * A request naming any other engine id is skipped.
 */
@Service
public class EngineScoringService {

    private static final Logger log = LoggerFactory.getLogger(EngineScoringService.class);

    private final Engine engine;

    public EngineScoringService(Engine engine) {
        this.engine = engine;
    }

    /**
     * @param engineId the the engine's id to run; if
     *                  it isn't loaded or isn't enabled, the result is empty
     *                  (no summaries, no diagnostics) rather than an error —
     *                  there's nothing meaningful to report about a engine
     *                  the caller couldn't legitimately have selected
     * @param policy   one of that engine's the engine's policies
     *                  values, or {@link Engine#DEFAULT_POLICY}
     */
    public AggregatedScoringResult scoreOne(String rawSpec, String engineId, String policy) {
        if (engineId == null || engineId.isBlank()) {
            return new AggregatedScoringResult(List.of(), List.of(), -1, Double.NaN, Double.NaN);
        }
        return scoreMany(rawSpec, List.of(new EngineRunRequest(engineId, policy)));
    }

    /**
     * Runs every requested engine against the same spec and merges the
     * results: {@link AggregatedScoringResult#engineSummaries()} keeps
     * one entry per engine (so a caller can tell which one, say, errored),
     * while {@link AggregatedScoringResult#diagnostics()} is the
     * flattened, engine-tagged union — this is what lets an endpoint's
     * findings table show a general linter's complaints side by side with a
     * specialised engine's (e.g. a breaking-changes checker) without either
     * needing to know the other exists. A {@code engineId} that isn't
     * loaded/enabled is silently skipped, same as {@link #scoreOne}.
     *
     * <p>{@code overallScore}/{@code overallScoreWithoutBlockers} are only
     * ever taken from a single engine's own result, never combined —
     * averaging (or otherwise merging) two unrelated scoring models'
     * outputs into one number wouldn't mean anything, so a run with more
     * than one engine reporting a score leaves both {@link Double#NaN}
     * ("not computed") rather than fabricate a combined figure.
     */
    public AggregatedScoringResult scoreMany(String rawSpec, List<EngineRunRequest> requests) {
        SpecFormat format = detectFormat(rawSpec);
        List<ScoringSummary> summaries = new ArrayList<>();
        List<AttributedDiagnostic> diagnostics = new ArrayList<>();
        List<ScoringResult> successfulResults = new ArrayList<>();
        String grade = null;
        double passingScore = Double.NaN;

        for (EngineRunRequest request : requests) {
            Engine scorer = findEnabled(request.engineId());
            if (scorer == null) {
                continue;
            }
            String policy = request.policy();
            String resolvedPolicy = policy == null || policy.isBlank() ? SpecInput.DEFAULT_POLICY : policy;
            SpecInput input = SpecInput.builder().content(rawSpec).format(format).policy(resolvedPolicy).build();
            ScoringResult result = runOne(scorer, input);
            summaries.add(toSummary(scorer, result));
            if (result.getStatus() == ScoringResult.Status.SUCCESS) {
                for (Diagnostic diagnostic : result.getDiagnostics()) {
                    diagnostics.add(new AttributedDiagnostic(scorer.getId(), scorer.getName(), diagnostic));
                }
                successfulResults.add(result);
                if (requests.size() == 1) {
                    grade = result.getGrade();
                    try {
                        passingScore = scorer.getPassingScore(resolvedPolicy).orElse(Double.NaN);
                    } catch (Throwable ignored) {
                        // a engine can't be trusted to behave; leave passingScore NaN
                    }
                }
            }
        }

        int rulesEvaluatedCount = combinedRulesEvaluatedCount(successfulResults);
        double overallScore = successfulResults.size() == 1 ? successfulResults.get(0).getOverallScore() : Double.NaN;
        double overallScoreWithoutBlockers =
                successfulResults.size() == 1 ? successfulResults.get(0).getOverallScoreWithoutBlockers() : Double.NaN;

        return new AggregatedScoringResult(summaries, diagnostics, rulesEvaluatedCount,
                overallScore, overallScoreWithoutBlockers, grade, passingScore);
    }

    /** Sums whichever results actually reported a count; {@code -1} ("unknown") if none did. */
    private static int combinedRulesEvaluatedCount(List<ScoringResult> results) {
        int total = -1;
        for (ScoringResult result : results) {
            if (result.getRulesEvaluatedCount() >= 0) {
                total = (total < 0 ? 0 : total) + result.getRulesEvaluatedCount();
            }
        }
        return total;
    }

    private Engine findEnabled(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            return null;
        }
        return Engine.ID.equals(engineId) ? engine : null;
    }

    private static ScoringResult runOne(Engine scorer, SpecInput input) {
        try {
            return scorer.score(input);
        } catch (Throwable t) {
            // Defensive backstop per the interface's documented contract: a engine
            // must never be able to break a scoring run for the whole host.
            log.warn("Scoring engine '{}' threw unexpectedly: {}", scorer.getId(), t.toString());
            return ScoringResult.pluginError(t.toString());
        }
    }

    private static ScoringSummary toSummary(Engine scorer, ScoringResult result) {
        return switch (result.getStatus()) {
            case SUCCESS -> new ScoringSummary(
                    scorer.getName(), "SUCCESS", result.getDiagnostics().size(), null);
            case PARSE_ERROR, PLUGIN_ERROR -> new ScoringSummary(
                    scorer.getName(), result.getStatus().name(), 0, result.getErrorMessage());
        };
    }

    private static SpecFormat detectFormat(String rawSpec) {
        if (rawSpec.contains("\"swagger\"") || rawSpec.matches("(?s).*(^|\\n)\\s*swagger\\s*:.*")) {
            return SpecFormat.SWAGGER2;
        }
        return SpecFormat.OPENAPI3;
    }
}
