package net.dublinux.arete.engine.gate;

import net.dublinux.arete.engine.report.ScoreDiff;
import net.dublinux.arete.engine.report.ScoreReport;

import java.util.List;

/**
 * What the gate decided about one spec. {@code base} and {@code diff} are null for a new spec, which has nothing to be
 * compared with and so has to stand on its own. {@code warning} notes something worth knowing that did not decide the
 * result, such as a base that did not parse.
 */
public record SpecResult(String file, boolean isNew, ScoreReport head, ScoreReport base, ScoreDiff diff,
        boolean passed, List<String> reasons, String warning) {
    public SpecResult {
        reasons = List.copyOf(reasons);
    }
}
