package net.dublinux.arete.scoring.spi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single rule diagnostic found in a spec.
 *
 * <p>Deliberately a plain immutable class with a builder rather than a
 * {@code record} — per non-functional requirement #3, adding an optional
 * field later (as happened with {@code lineNumber} and {@code
 * documentationUrl}) means adding a builder method with a safe default and
 * one new getter, and every already-built plugin jar keeps compiling
 * against the class unchanged. A record's
 * canonical constructor is part of its public signature, so adding a
 * component breaks every already-compiled plugin jar that calls the
 * constructor positionally, forcing an adapter rebuild for every plugin
 * whenever the DTO grows. The builder absorbs that churn instead.
 *
 * <p>Open question — score improvement: {@link #getScoreImprovement()} is
 * per-diagnostic rather than a separate per-rule summary method so the host
 * UI can render it right next to that specific diagnostic's own
 * accept/waive control — e.g. "+4.2 pts if resolved" beside that finding's
 * checkbox — without a second lookup from diagnostic to rule to score. It's
 * optional and {@link Double#NaN}-defaulted for the same reason {@link
 * #getLineNumber()} and {@link #getDocumentationUrl()} are: not every
 * engine has a scoring model at all, and forcing one would either
 * fabricate numbers or block adapters for engines that don't. A host must
 * hide the indicator entirely for a {@code NaN} value — never render
 * "NaN pts" or fall back to "0 pts", since a genuinely zero-impact
 * diagnostic is a real, distinct value from "not computed".
 */
public final class Diagnostic {

    private final String ruleId;
    private final String title;
    private final String description;
    private final Severity severity;
    private final String pointer;
    private final List<String> paths;
    private final Integer lineNumber;
    private final String documentationUrl;
    private final double scoreImprovement;

    private Diagnostic(Builder b) {
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId must not be null");
        this.title = Objects.requireNonNull(b.title, "title must not be null");
        this.description = b.description;
        this.severity = Objects.requireNonNull(b.severity, "severity must not be null");
        this.pointer = b.pointer;
        this.paths = b.paths == null ? Collections.emptyList() : List.copyOf(b.paths);
        this.lineNumber = b.lineNumber;
        this.documentationUrl = b.documentationUrl;
        this.scoreImprovement = b.scoreImprovement;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getTitle() {
        return title;
    }

    /** Nullable / may be empty. */
    public String getDescription() {
        return description;
    }

    public Severity getSeverity() {
        return severity;
    }

    /** JSON Pointer into the spec; nullable if not applicable. */
    public String getPointer() {
        return pointer;
    }

    /**
     * Every endpoint this diagnostic affects, each formatted {@code "<METHOD>
     * <path>"} (e.g. {@code "GET /items/{id}"}, method upper-case) — never
     * null, may be empty. This is how a rule that's evaluated once but
     * flags several endpoints (a naming-convention check across an entire
     * spec, say) reports all of them, rather than the host only ever
     * learning about the one endpoint {@link #getPointer()} happens to
     * point at. A host attributes this diagnostic to every endpoint named
     * here in addition to whatever {@link #getPointer()} resolves to, so
     * list every affected endpoint even if one of them is also the
     * pointer's own operation — duplicates are harmless.
     */
    public List<String> getPaths() {
        return paths;
    }

    /**
     * 1-based line number of the diagnostic within the raw spec text passed
     * in {@link SpecInput#getContent()}, for editors/UIs that want to jump
     * straight to the offending line. Nullable — not every engine tracks
     * source positions (some work purely against the parsed object graph),
     * and even engines that do may not report a line for every diagnostic
     * (e.g. a "missing top-level field" finding has no single line). {@link
     * #getPointer()} remains the structural (JSON Pointer) location and is
     * the more reliable field when both are absent/present; prefer
     * {@code lineNumber} when present since it's cheaper for a UI to
     * highlight directly, and fall back to resolving {@code pointer}
     * against the parsed spec otherwise.
     */
    public Integer getLineNumber() {
        return lineNumber;
    }

    /**
     * A URL pointing to human-readable documentation for this specific
     * rule (e.g. an engine's own rule doc page), so a UI can render "learn more"
     * next to the diagnostic. Nullable — not every rule has published docs.
     */
    public String getDocumentationUrl() {
        return documentationUrl;
    }

    /**
     * Score points — same 0–100 scale as {@link
     * ScoringResult#getOverallScore()} — gained if every diagnostic of
     * this diagnostic's rule were resolved, all other diagnostics held
     * constant. Per distinct rule, not per individual finding: if the same
     * rule fires 5 times, each of those 5 {@code Diagnostic}s reports the
     * same value, since "resolve one instance of this rule in isolation"
     * isn't a coherent question for most scoring models — the jump is
     * defined for resolving the rule, not a single finding of it.
     *
     * <p>Positive means resolving the rule helps the score, as expected —
     * but a plugin may legitimately report a negative value if its
     * scoring model has interactions where resolving one rule changes
     * eligibility for another rule's scoring bucket, lowering the total.
     * The host renders the sign explicitly ("+4.2 pts" / "−1.0 pts")
     * rather than assuming improvement is always positive.
     *
     * <p>{@link Double#NaN} — never {@code 0}, never assume it means
     * "no impact" — is "not computed": for an engine with no scoring
     * concept at all, or a plugin jar built before this field existed (it
     * defaults to {@code NaN} in {@link Builder}, so such a plugin keeps
     * compiling and simply never reports a score impact). A host must
     * check {@link Double#isNaN(double)} and hide the indicator entirely
     * next to that diagnostic's checkbox/toggle when {@code NaN} — a
     * genuinely zero-impact diagnostic (already fully priced into the
     * score via some other already-violated rule) is a real, valid value
     * distinct from "unknown".
     */
    public double getScoreImprovement() {
        return scoreImprovement;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String ruleId;
        private String title;
        private String description;
        private Severity severity;
        private String pointer;
        private List<String> paths;
        private Integer lineNumber;
        private String documentationUrl;
        private double scoreImprovement = Double.NaN;

        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder pointer(String pointer) {
            this.pointer = pointer;
            return this;
        }

        public Builder paths(List<String> paths) {
            this.paths = paths;
            return this;
        }

        public Builder lineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder documentationUrl(String documentationUrl) {
            this.documentationUrl = documentationUrl;
            return this;
        }

        /** See {@link Diagnostic#getScoreImprovement()}. Defaults to {@link Double#NaN} ("not computed") if never called. */
        public Builder scoreImprovement(double scoreImprovement) {
            this.scoreImprovement = scoreImprovement;
            return this;
        }

        public Diagnostic build() {
            return new Diagnostic(this);
        }
    }
}
