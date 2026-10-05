package net.dublinux.arete.engine.report;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two reports of the same spec (a base and a head) set side by side: each finding is NEW (only in the head),
 * EXISTING (in both) or RESOLVED (only in the base). A finding is the same finding when its rule and pointer
 * are, whatever its message says. Findings that share a rule and pointer pair off one for one.
 */
public record ScoreDiff(ScoreReport base, ScoreReport head, List<Change> changes) {
    public enum Kind { NEW, EXISTING, RESOLVED }

    /** One finding and what happened to it. A RESOLVED finding comes from the base, the others from the head. */
    public record Change(Kind kind, Finding finding) { }

    public ScoreDiff {
        changes = List.copyOf(changes);
    }

    public static ScoreDiff of(ScoreReport base, ScoreReport head) {
        Map<String, Deque<Finding>> remaining = new LinkedHashMap<>();
        for (Finding finding : base.findings()) remaining.computeIfAbsent(finding.identity(), k -> new ArrayDeque<>()).add(finding);
        List<Change> changes = new ArrayList<>();
        for (Finding finding : head.findings()) {
            Deque<Finding> same = remaining.get(finding.identity());
            if (same != null && !same.isEmpty()) {
                same.poll();
                changes.add(new Change(Kind.EXISTING, finding));
            } else {
                changes.add(new Change(Kind.NEW, finding));
            }
        }
        for (Deque<Finding> left : remaining.values()) for (Finding finding : left) changes.add(new Change(Kind.RESOLVED, finding));
        return new ScoreDiff(base, head, changes);
    }

    public List<Change> of(Kind kind) { return changes.stream().filter(c -> c.kind() == kind).toList(); }

    public long count(Kind kind) { return changes.stream().filter(c -> c.kind() == kind).count(); }

    /** Head score minus base score; negative is a regression. */
    public double scoreDelta() { return head.score() - base.score(); }

    public boolean regressed() { return head.score() < base.score(); }

    /** New findings at blocker (ERROR) level. */
    public long newBlockers() {
        return changes.stream().filter(c -> c.kind() == Kind.NEW && "ERROR".equals(c.finding().severity())).count();
    }
}
