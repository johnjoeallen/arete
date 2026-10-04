package net.dublinux.arete.engine.api;

/**
 * What one rule did in a run: how many times it matched, what it cost, and whether its cost depends on the count.
 * {@code graduated} is true for a rule scored per match or by tier, whose count can get worse without a new kind of
 * finding appearing, so a change gate compares its count and not only whether it fired.
 */
public record RuleOutcome(String ruleId, int count, double cost, boolean graduated, boolean prohibited) { }
