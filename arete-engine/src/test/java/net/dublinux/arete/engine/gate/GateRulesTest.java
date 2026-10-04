package net.dublinux.arete.engine.gate;

import net.dublinux.arete.engine.api.RuleOutcome;
import net.dublinux.arete.engine.report.Finding;
import net.dublinux.arete.engine.report.ScoreReport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gate's rules for count-scored rules: they fail only when newly violated or worse than before. */
class GateRulesTest {
    private static ScoreReport report(double score, int count, boolean graduated) {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < count; i++) findings.add(new Finding("LIMIT", "t", "WARNING", "Warning", "/p" + i, null, "m", i + 1, 1, 1.0));
        List<RuleOutcome> outcomes = count == 0 ? List.of() : List.of(new RuleOutcome("LIMIT", count, 1.0, graduated, false));
        return new ScoreReport("f.yaml", "P", "SUCCESS", null, score, score, "A", 90.0, 1, findings, List.of(), outcomes);
    }

    @Test
    void aCountThatRisesFailsEvenWhenTheCostDoesNot() {
        // Per-match capped at 1 point: five matches and two matches cost the same.
        SpecResult result = GateRules.evaluate("f.yaml", report(99, 5, true), report(99, 2, true), null);

        assertFalse(result.passed());
        assertEquals("LIMIT got worse: 2 -> 5, costing 1", result.reasons().get(0));
    }

    @Test
    void aSameOrLowerCountPasses() {
        assertTrue(GateRules.evaluate("f.yaml", report(99, 3, true), report(99, 3, true), null).passed());
        assertTrue(GateRules.evaluate("f.yaml", report(99, 1, true), report(99, 3, true), null).passed());
    }

    @Test
    void aCountRuleNewlyViolatedFailsAndSaysSo() {
        SpecResult result = GateRules.evaluate("f.yaml", report(99, 2, true), report(100, 0, true), null);

        assertFalse(result.passed());
        assertTrue(result.reasons().get(0).contains("0 -> 2 (newly violated)"), result.reasons().toString());
    }

    @Test
    void aFlatRuleWithMoreFindingsIsNotJudgedByItsCount() {
        // The same warning rule firing in two more places costs nothing more, so it is existing debt, not a regression.
        assertTrue(GateRules.evaluate("f.yaml", report(99, 5, false), report(99, 2, false), null).passed());
    }

    /** A rule charged by value: the count of matches stays the same, the worst value rises. */
    private static ScoreReport byValue(double score, double worst) {
        List<Finding> findings = List.of(new Finding("DEPTH", "t", "WARNING", "Warning", "/s", null, "m", 1, 1, 1.0));
        List<RuleOutcome> outcomes = List.of(new RuleOutcome("DEPTH", 1, worst, 1.0, true, false));
        return new ScoreReport("f.yaml", "P", "SUCCESS", null, score, score, "A", 90.0, 1, findings, List.of(), outcomes);
    }

    @Test
    void aValueThatRisesFailsEvenWhenTheCountAndTheCostDoNot() {
        SpecResult result = GateRules.evaluate("f.yaml", byValue(99, 7), byValue(99, 5), null);

        assertFalse(result.passed());
        assertEquals("DEPTH got worse: 5 -> 7, costing 1", result.reasons().get(0));
        assertTrue(GateRules.evaluate("f.yaml", byValue(99, 5), byValue(99, 5), null).passed());
    }
}
