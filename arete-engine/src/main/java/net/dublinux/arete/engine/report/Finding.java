package net.dublinux.arete.engine.report;

/**
 * One finding in a report. {@code severity} is the stable name (ERROR, WARNING, INFO, HINT); {@code severityLabel} is
 * what the engine calls it (an ERROR is a "Blocker" in the policy engine). {@code line} and {@code column} are 1-based and null when the spec's text could not
 * be read. {@code scoreImpact} is the points the finding's rule costs (0 for a {@code PROHIBITED} rule, whose
 * cost is the whole score).
 */
public record Finding(String ruleId, String title, String severity, String severityLabel, String pointer, String message,
        Integer line, Integer column, double scoreImpact) {

    /** What makes two findings the same finding in two runs: the rule and the place, not the wording. */
    public String identity() { return ruleId + "|" + pointer; }
}
