package net.dublinux.arete.cigate;

import java.util.Map;

/**
 * One combination's server-computed result. The plugin never recomputes any
 * of this — {@link #met()} is Areté's verdict for the combination against its
 * own policy (or the build-wide {@code failOn} override).
 *
 * @param optional whether the build declared this combination non-gating
 * @param status   the scoring run status, {@code "SUCCESS"} when it completed
 * @param met      whether the combination cleared its level
 */
public record CombinationOutcome(
        String validator,
        String policy,
        boolean optional,
        String status,
        Double score,
        String grade,
        Double passingScore,
        String levelCriterion,
        String levelSource,
        boolean met,
        Map<String, Integer> counts) {

    public CombinationOutcome {
        counts = counts == null ? Map.of() : Map.copyOf(counts);
    }

    public String run() {
        return validator + "/" + policy;
    }

    /** The scoring run itself completed (regardless of pass/fail). */
    public boolean scored() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    /** Counts toward the build gate: gating, and either failed or errored. */
    public boolean gatingFailure() {
        return !optional && (!scored() || !met);
    }
}
