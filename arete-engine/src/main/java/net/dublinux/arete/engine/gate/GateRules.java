package net.dublinux.arete.engine.gate;

import net.dublinux.arete.engine.api.RuleOutcome;
import net.dublinux.arete.engine.report.Finding;
import net.dublinux.arete.engine.report.ScoreDiff;
import net.dublinux.arete.engine.report.ScoreReport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gate's rules, which judge a change rather than a spec: the debt a spec already has is not re-argued.
 *
 * <ul>
 *   <li>A changed spec fails on any new blocker (an ERROR finding, from a PROHIBITED rule), or when its score is lower
 *       than the base's.</li>
 *   <li>A new spec has nothing to be compared with, so it must meet the policy's pass mark and have no blockers.</li>
 *   <li>A head that does not parse fails; one the engine cannot score is a configuration problem.</li>
 * </ul>
 */
final class GateRules {
    private GateRules() { }

    static SpecResult evaluate(String file, ScoreReport head, ScoreReport base, String warning) {
        List<String> reasons = new ArrayList<>();
        if (!head.succeeded()) {
            reasons.add(("PARSE_ERROR".equals(head.status()) ? "does not parse: " : "could not be scored: ") + head.errorMessage());
            return new SpecResult(file, base == null, head, base, null, false, reasons, warning);
        }
        if (base == null) {
            if (head.passingScore() != null && head.score() < head.passingScore()) {
                reasons.add("new spec scores " + number(head.score()) + ", below the " + head.policy() + " pass mark of " + number(head.passingScore()));
            }
            long blockers = head.findings().stream().filter(f -> "ERROR".equals(f.severity())).count();
            if (blockers > 0) {
                reasons.add("new spec has " + blockers + (blockers == 1 ? " blocker" : " blockers"));
                for (Finding f : head.findings()) if ("ERROR".equals(f.severity())) reasons.add("  " + describe(file, f));
            }
            return new SpecResult(file, true, head, null, null, reasons.isEmpty(), reasons, warning);
        }

        ScoreDiff diff = ScoreDiff.of(base, head);
        for (ScoreDiff.Change change : diff.of(ScoreDiff.Kind.NEW)) {
            if ("ERROR".equals(change.finding().severity())) reasons.add("new blocker: " + describe(file, change.finding()));
        }
        // A rule scored by count (per match, or by tier) can get worse without a new kind of finding: compare its count, or the value it measures.
        Map<String, Double> baseCounts = new LinkedHashMap<>();
        for (RuleOutcome outcome : base.ruleOutcomes()) baseCounts.put(outcome.ruleId(), outcome.measure());
        for (RuleOutcome outcome : head.ruleOutcomes()) {
            if (!outcome.graduated()) continue;
            double before = baseCounts.getOrDefault(outcome.ruleId(), 0.0);
            if (outcome.measure() > before) {
                reasons.add(outcome.ruleId() + " got worse: " + number(before) + " -> " + number(outcome.measure()) + (before == 0 ? " (newly violated)" : "")
                        + ", costing " + number(outcome.cost()));
            }
        }
        if (diff.regressed()) {
            reasons.add("score fell from " + number(base.score()) + " to " + number(head.score()) + " (" + number(diff.scoreDelta()) + ")" + caused(diff));
        }
        return new SpecResult(file, false, head, base, diff, reasons.isEmpty(), reasons, warning);
    }

    /** What the drop is owed to: rules the head violates that the base did not, each costing its points once. */
    private static String caused(ScoreDiff diff) {
        Map<String, Double> baseRules = new LinkedHashMap<>();
        for (Finding f : diff.base().findings()) baseRules.putIfAbsent(f.ruleId(), f.scoreImpact());
        Map<String, Double> newRules = new LinkedHashMap<>();
        for (Finding f : diff.head().findings()) if (!baseRules.containsKey(f.ruleId())) newRules.putIfAbsent(f.ruleId(), f.scoreImpact());
        if (newRules.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> rule : newRules.entrySet()) {
            parts.add(rule.getKey() + (rule.getValue() > 0 ? " (-" + number(rule.getValue()) + ")" : " (blocker)"));
        }
        return "; newly violated: " + String.join(", ", parts);
    }

    private static String describe(String file, Finding f) {
        return f.ruleId() + " at " + (f.line() == null ? file : file + ":" + f.line()) + " (" + f.pointer() + ") - " + f.message();
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
