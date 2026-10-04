package net.dublinux.arete.engine.report;

import java.util.List;

/**
 * One finding in a report. {@code severity} is the stable name (ERROR, WARNING, INFO, HINT); {@code severityLabel} is
 * what the engine calls it (an ERROR is a "Blocker" in the policy engine). {@code line} and {@code column} are 1-based and null when the spec's text could not
 * be read. {@code path} is the rule's label for the subject, such as {@code GET /orders}. {@code routes} lists the ways in from each operation to the shared definition the finding sits on, shortest first (empty otherwise; it is not part of the finding's identity). {@code scoreImpact} is the points the finding's rule costs (0 for a {@code PROHIBITED} rule, whose
 * cost is the whole score).
 */
public record Finding(String ruleId, String title, String severity, String severityLabel, String pointer, String path,
        String message, Integer line, Integer column, double scoreImpact, List<List<String>> routes) {

    public Finding {
        routes = routes == null ? List.of() : routes.stream().map(List::copyOf).toList();
    }

    /** A finding that is not on a shared definition, so has no route to show. */
    public Finding(String ruleId, String title, String severity, String severityLabel, String pointer, String path,
            String message, Integer line, Integer column, double scoreImpact) {
        this(ruleId, title, severity, severityLabel, pointer, path, message, line, column, scoreImpact, List.of());
    }

    /** One route as a line, {@code POST /orders → Order → Address}. */
    public static String routeText(List<String> route) { return String.join(" → ", route); }

    /**
     * What makes two findings the same finding in two runs: the rule and the place, not the wording. The place is
     * the pointer plus the rule's own label for the subject ("GET /orders", a server's URL), which tells apart the
     * findings of one rule that share a pointer.
     */
    public String identity() { return ruleId + "|" + pointer + "|" + (path == null ? "" : path); }
}
