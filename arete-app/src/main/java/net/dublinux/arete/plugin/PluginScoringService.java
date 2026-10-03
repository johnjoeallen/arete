package net.dublinux.arete.plugin;

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
public class PluginScoringService {

    private static final Logger log = LoggerFactory.getLogger(PluginScoringService.class);

    private final Engine engine;

    public PluginScoringService(Engine engine) {
        this.engine = engine;
    }

    /**
     * @param pluginId the the engine's id to run; if
     *                  it isn't loaded or isn't enabled, the result is empty
     *                  (no summaries, no diagnostics) rather than an error —
     *                  there's nothing meaningful to report about a plugin
     *                  the caller couldn't legitimately have selected
     * @param policy   one of that plugin's the engine's policies
     *                  values, or {@link Engine#DEFAULT_POLICY}
     */
    public AggregatedScoringResult scoreOne(String rawSpec, String pluginId, String policy) {
        if (pluginId == null || pluginId.isBlank()) {
            return new AggregatedScoringResult(List.of(), List.of(), -1, Double.NaN, Double.NaN);
        }
        return scoreMany(rawSpec, List.of(new PluginRunRequest(pluginId, policy)));
    }

    /**
     * Runs every requested plugin against the same spec and merges the
     * results: {@link AggregatedScoringResult#pluginSummaries()} keeps
     * one entry per plugin (so a caller can tell which one, say, errored),
     * while {@link AggregatedScoringResult#diagnostics()} is the
     * flattened, plugin-tagged union — this is what lets an endpoint's
     * findings table show a general linter's complaints side by side with a
     * specialised plugin's (e.g. a breaking-changes checker) without either
     * needing to know the other exists. A {@code pluginId} that isn't
     * loaded/enabled is silently skipped, same as {@link #scoreOne}.
     *
     * <p>{@code overallScore}/{@code overallScoreWithoutBlockers} are only
     * ever taken from a single plugin's own result, never combined —
     * averaging (or otherwise merging) two unrelated scoring models'
     * outputs into one number wouldn't mean anything, so a run with more
     * than one plugin reporting a score leaves both {@link Double#NaN}
     * ("not computed") rather than fabricate a combined figure.
     */
    public AggregatedScoringResult scoreMany(String rawSpec, List<PluginRunRequest> requests) {
        SpecFormat format = detectFormat(rawSpec);
        List<ScoringSummary> summaries = new ArrayList<>();
        List<AttributedDiagnostic> diagnostics = new ArrayList<>();
        List<ScoringResult> successfulResults = new ArrayList<>();
        String grade = null;
        double passingScore = Double.NaN;

        for (PluginRunRequest request : requests) {
            Engine plugin = findEnabled(request.pluginId());
            if (plugin == null) {
                continue;
            }
            String policy = request.policy();
            String resolvedPolicy = policy == null || policy.isBlank() ? SpecInput.DEFAULT_POLICY : policy;
            SpecInput input = SpecInput.builder().content(rawSpec).format(format).policy(resolvedPolicy).build();
            ScoringResult result = runOne(plugin, input);
            summaries.add(toSummary(plugin, result));
            if (result.getStatus() == ScoringResult.Status.SUCCESS) {
                for (Diagnostic diagnostic : result.getDiagnostics()) {
                    diagnostics.add(new AttributedDiagnostic(plugin.getId(), plugin.getName(), diagnostic));
                }
                successfulResults.add(result);
                if (requests.size() == 1) {
                    grade = result.getGrade();
                    try {
                        passingScore = plugin.getPassingScore(resolvedPolicy).orElse(Double.NaN);
                    } catch (Throwable ignored) {
                        // a plugin can't be trusted to behave; leave passingScore NaN
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

    private Engine findEnabled(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }
        return Engine.ID.equals(pluginId) ? engine : null;
    }

    private static ScoringResult runOne(Engine plugin, SpecInput input) {
        try {
            return plugin.score(input);
        } catch (Throwable t) {
            // Defensive backstop per the interface's documented contract: a plugin
            // must never be able to break a scoring run for the whole host.
            log.warn("Scoring plugin '{}' threw unexpectedly: {}", plugin.getId(), t.toString());
            return ScoringResult.pluginError(t.toString());
        }
    }

    private static ScoringSummary toSummary(Engine plugin, ScoringResult result) {
        return switch (result.getStatus()) {
            case SUCCESS -> new ScoringSummary(
                    plugin.getName(), "SUCCESS", result.getDiagnostics().size(), null);
            case PARSE_ERROR, PLUGIN_ERROR -> new ScoringSummary(
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
