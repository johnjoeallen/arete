package net.dublinux.arete.engine.report;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.api.Diagnostic;
import net.dublinux.arete.engine.api.RuleOutcome;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The result of scoring one spec, in the shape the report writers and the diff work from. */
public record ScoreReport(String file, String policy, String status, String errorMessage, double score,
        double scoreWithoutBlockers, String grade, Double passingScore, int rulesEvaluated,
        List<Finding> findings, List<Overrides.RuleOverride> overridesApplied, List<RuleOutcome> ruleOutcomes) {

    public ScoreReport {
        findings = List.copyOf(findings);
        overridesApplied = List.copyOf(overridesApplied);
        ruleOutcomes = List.copyOf(ruleOutcomes);
    }

    /** A report without per-rule counts, for engines that do not give them. */
    public ScoreReport(String file, String policy, String status, String errorMessage, double score,
            double scoreWithoutBlockers, String grade, Double passingScore, int rulesEvaluated,
            List<Finding> findings, List<Overrides.RuleOverride> overridesApplied) {
        this(file, policy, status, errorMessage, score, scoreWithoutBlockers, grade, passingScore, rulesEvaluated, findings, overridesApplied, List.of());
    }

    public boolean succeeded() { return "SUCCESS".equals(status); }

    /** True when the policy states a pass mark and the score reaches it; true when it states none. */
    public boolean meetsPassingScore() { return passingScore == null || (succeeded() && score >= passingScore); }

    /**
     * Scores {@code text} with {@code engine} and builds the report. {@code policy} may be null for the
     * engine's default; {@code overrides} may be {@link Overrides#none()}.
     */
    public static ScoreReport of(Engine engine, String file, String text, String policy, Overrides overrides) {
        String policyName = policy == null || policy.isBlank() ? SpecInput.DEFAULT_POLICY : policy;
        SpecFormat format = text.contains("\"swagger\"") || text.matches("(?s).*(^|\\n)\\s*swagger\\s*:.*") ? SpecFormat.SWAGGER2 : SpecFormat.OPENAPI3;
        ScoringResult result = engine.score(SpecInput.builder().content(text).format(format).policy(policyName).build(), overrides);
        PointerLocator locator = PointerLocator.of(text);
        RefPaths routes = RefPaths.of(text);
        List<Finding> findings = new ArrayList<>();
        for (Diagnostic d : result.getDiagnostics()) {
            PointerLocator.Location at = locator.locate(d.getPointer());
            double impact = Double.isNaN(d.getScoreImprovement()) ? 0 : d.getScoreImprovement();
            findings.add(new Finding(d.getRuleId(), d.getTitle(), d.getSeverity().name(), engine.getSeverityLabel(d.getSeverity()), d.getPointer(),
                    d.getPaths().isEmpty() ? null : d.getPaths().get(0),
                    d.getDescription() == null ? d.getTitle() : d.getDescription(),
                    at == null ? null : at.line(), at == null ? null : at.column(), impact, routes.routeTo(d.getPointer())));
        }
        findings.sort(Comparator.comparing((Finding f) -> f.line() == null ? Integer.MAX_VALUE : f.line())
                .thenComparing(Finding::ruleId).thenComparing(f -> String.valueOf(f.pointer())));
        List<String> policies = engine.getPolicies();
        String resolvedPolicy = policies.contains(policyName) || policies.isEmpty() ? policyName : policies.get(0);
        Double passing = engine.getPassingScore(resolvedPolicy).isPresent() ? engine.getPassingScore(resolvedPolicy).getAsDouble() : null;
        return new ScoreReport(file, resolvedPolicy, result.getStatus().name(), result.getErrorMessage(),
                result.getOverallScore(), result.getOverallScoreWithoutBlockers(), result.getGrade(), passing,
                result.getRulesEvaluatedCount(), findings, List.copyOf(overrides.rules().values()), result.getRuleOutcomes());
    }
}
