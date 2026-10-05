package net.dublinux.arete.engine.api;

/**
 * What one rule did in a run: how many times it matched, what its policy measured, what it cost, and whether its cost
 * depends on that measure. {@code measure} is the match count, or, for a rule a policy charges by value, the largest value
 * its matcher reported (the deepest schema, the operation with the most parameters). {@code graduated} is true for a rule
 * scored per match or by tier, which can get worse without a new kind of finding appearing, so a change gate compares its
 * measure and not only whether it fired.
 */
public record RuleOutcome(String ruleId, int count, double measure, double cost, boolean graduated, boolean prohibited) {
    /** A rule measured by its match count. */
    public RuleOutcome(String ruleId, int count, double cost, boolean graduated, boolean prohibited) {
        this(ruleId, count, count, cost, graduated, prohibited);
    }
}
