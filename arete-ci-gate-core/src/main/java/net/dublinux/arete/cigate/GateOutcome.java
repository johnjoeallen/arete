package net.dublinux.arete.cigate;

import java.util.List;

/**
 * The result of a submit-and-score: the per-combination outcomes plus the
 * one thing the build cares about — {@link #buildPassed()}, the logical AND
 * of every <em>non-optional</em> combination's verdict.
 */
public final class GateOutcome {

    private final String specId;
    private final List<CombinationOutcome> combinations;
    private final String sarif;

    public GateOutcome(String specId, List<CombinationOutcome> combinations, String sarif) {
        this.specId = specId;
        this.combinations = List.copyOf(combinations);
        this.sarif = sarif;
    }

    public String specId() {
        return specId;
    }

    public List<CombinationOutcome> combinations() {
        return combinations;
    }

    /** SARIF 2.1.0 log for the run, or {@code null} if none was requested. */
    public String sarif() {
        return sarif;
    }

    public boolean buildPassed() {
        return combinations.stream().noneMatch(CombinationOutcome::gatingFailure);
    }

    public List<CombinationOutcome> gatingFailures() {
        return combinations.stream().filter(CombinationOutcome::gatingFailure).toList();
    }
}
