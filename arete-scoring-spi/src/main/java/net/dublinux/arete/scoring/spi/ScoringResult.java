package net.dublinux.arete.scoring.spi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a single {@link SpecScoringPlugin#validate} call.
 *
 * <p>Open question re: error handling — everything is represented as data
 * on this result rather than as checked exceptions thrown from
 * {@code score()}. Reasons:
 *
 * <ul>
 *   <li>A checked exception type would itself have to live in the
 *       interface module (to be catchable by the host across the
 *       classloader boundary per constraint #5) and cross that boundary on
 *       every failure path — but "spec didn't parse" and "engine hit a
 *       rule it couldn't evaluate" are routine, expected outcomes for a
 *       linter, not exceptional control flow. Modeling them as data keeps
 *       {@code score()}'s signature simple ({@code throws} nothing
 *       checked) and makes host-side handling a switch over
 *       {@link Status} instead of a try/catch ladder.</li>
 *   <li>Genuine plugin bugs (NPEs, engine crashes the adapter didn't
 *       anticipate) are a different case — see {@link
 *       SpecScoringPlugin#validate} javadoc: the host wraps the call in
 *       a defensive {@code catch (Throwable)} regardless, since a
 *       misbehaving plugin jar can never be allowed to take down the host
 *       process. {@code PLUGIN_ERROR} is for the case where the adapter
 *       itself catches an unexpected failure and wants to report it
 *       gracefully rather than propagate it.</li>
 * </ul>
 */
public final class ScoringResult {

    public enum Status {
        /** Spec parsed successfully; check {@link #getDiagnostics()} (may be empty). */
        SUCCESS,
        /** The input was not parseable as the declared {@link SpecFormat} at all. */
        PARSE_ERROR,
        /** The plugin/engine itself failed unexpectedly while validating. */
        PLUGIN_ERROR
    }

    private final Status status;
    private final List<Diagnostic> diagnostics;
    private final String errorMessage;
    private final int rulesEvaluatedCount;
    private final double overallScore;
    private final double overallScoreWithoutBlockers;
    private final String grade;

    private ScoringResult(Builder b) {
        this.status = Objects.requireNonNull(b.status, "status must not be null");
        this.diagnostics = b.diagnostics == null ? Collections.emptyList() : List.copyOf(b.diagnostics);
        this.errorMessage = b.errorMessage;
        this.rulesEvaluatedCount = b.rulesEvaluatedCount;
        this.overallScore = b.overallScore;
        this.overallScoreWithoutBlockers = b.overallScoreWithoutBlockers;
        this.grade = b.grade;
    }

    public Status getStatus() {
        return status;
    }

    /** Never null. Empty on PARSE_ERROR/PLUGIN_ERROR, or on a fully compliant spec. */
    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /** Human-readable detail for PARSE_ERROR/PLUGIN_ERROR; nullable otherwise. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Open question #5 (rule-evaluation summary): rather than a separate
     * method, a single {@code int} count was added here, defaulting to -1
     * ("unknown / not reported") so plugins that can't easily produce this
     * number aren't forced to. A plugin that tracks it can report e.g. 42;
     * the host can show "spec is fully compliant, 42 rules checked" when
     * present and fall back to "no diagnostics found" when -1. A full
     * per-rule breakdown (which specific rules passed) was deliberately
     * left out of v1 — it roughly doubles the DTO surface for a feature
     * only worth building once a plugin author actually asks for it.
     */
    public int getRulesEvaluatedCount() {
        return rulesEvaluatedCount;
    }

    /**
     * Overall compliance score for the validated spec, 0–100, or {@link
     * Double#NaN} if this engine has no scoring concept (or the plugin jar
     * predates this field — it defaults to {@code NaN} in {@link Builder}).
     * A new, standalone, optional concept: not every linter can reduce a
     * spec's diagnostics to a single number, and this SPI doesn't require
     * one to. Callers must check {@link Double#isNaN(double)} rather than
     * treating {@code 0} as "no score" — a spec that's genuinely scored at
     * zero is a real, valid value distinct from "not computed".
     *
     * @see #getOverallScoreWithoutBlockers()
     * @see Diagnostic#getScoreImprovement()
     */
    public double getOverallScore() {
        return overallScore;
    }

    /**
     * {@link #getOverallScore()} recomputed as though every blocker-severity
     * ({@link Severity#ERROR}) diagnostic were already resolved, all other
     * diagnostics held constant — for a plugin whose scoring model
     * distinguishes "how compliant is this spec today" from "how compliant
     * could it be once the must-fix items are gone". Same {@link
     * Double#NaN} "not computed" convention as {@link #getOverallScore()}.
     */
    public double getOverallScoreWithoutBlockers() {
        return overallScoreWithoutBlockers;
    }

    /**
     * A grade label for {@link #getOverallScore()} — {@code "A"}, {@code "B"},
     * a band name, whatever the engine's policy defines — or {@code null} if
     * the engine has no grading concept for this run. Purely a presentation
     * label over the numeric score; callers that gate on a threshold should
     * still use the number.
     */
    public String getGrade() {
        return grade;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ScoringResult success(List<Diagnostic> diagnostics, int rulesEvaluatedCount) {
        return builder()
                .status(Status.SUCCESS)
                .diagnostics(diagnostics)
                .rulesEvaluatedCount(rulesEvaluatedCount)
                .build();
    }

    public static ScoringResult parseError(String message) {
        return builder().status(Status.PARSE_ERROR).errorMessage(message).build();
    }

    public static ScoringResult pluginError(String message) {
        return builder().status(Status.PLUGIN_ERROR).errorMessage(message).build();
    }

    public static final class Builder {
        private Status status;
        private List<Diagnostic> diagnostics;
        private String errorMessage;
        private int rulesEvaluatedCount = -1;
        private double overallScore = Double.NaN;
        private double overallScoreWithoutBlockers = Double.NaN;
        private String grade;

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder diagnostics(List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder rulesEvaluatedCount(int rulesEvaluatedCount) {
            this.rulesEvaluatedCount = rulesEvaluatedCount;
            return this;
        }

        /** See {@link ScoringResult#getOverallScore()}. Defaults to {@link Double#NaN} ("not computed") if never called. */
        public Builder overallScore(double overallScore) {
            this.overallScore = overallScore;
            return this;
        }

        /** See {@link ScoringResult#getOverallScoreWithoutBlockers()}. Defaults to {@link Double#NaN} ("not computed") if never called. */
        public Builder overallScoreWithoutBlockers(double overallScoreWithoutBlockers) {
            this.overallScoreWithoutBlockers = overallScoreWithoutBlockers;
            return this;
        }

        /** See {@link ScoringResult#getGrade()}. Defaults to {@code null} if never called. */
        public Builder grade(String grade) {
            this.grade = grade;
            return this;
        }

        public ScoringResult build() {
            return new ScoringResult(this);
        }
    }
}
