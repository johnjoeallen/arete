package speculate.validation.spi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a single {@link SpecValidationPlugin#validate} call.
 *
 * <p>Open question re: error handling — everything is represented as data
 * on this result rather than as checked exceptions thrown from
 * {@code validate()}. Reasons:
 *
 * <ul>
 *   <li>A checked exception type would itself have to live in the
 *       interface module (to be catchable by the host across the
 *       classloader boundary per constraint #5) and cross that boundary on
 *       every failure path — but "spec didn't parse" and "engine hit a
 *       rule it couldn't evaluate" are routine, expected outcomes for a
 *       linter, not exceptional control flow. Modeling them as data keeps
 *       {@code validate()}'s signature simple ({@code throws} nothing
 *       checked) and makes host-side handling a switch over
 *       {@link Status} instead of a try/catch ladder.</li>
 *   <li>Genuine plugin bugs (NPEs, engine crashes the adapter didn't
 *       anticipate) are a different case — see {@link
 *       SpecValidationPlugin#validate} javadoc: the host wraps the call in
 *       a defensive {@code catch (Throwable)} regardless, since a
 *       misbehaving plugin jar can never be allowed to take down the host
 *       process. {@code PLUGIN_ERROR} is for the case where the adapter
 *       itself catches an unexpected failure and wants to report it
 *       gracefully rather than propagate it.</li>
 * </ul>
 */
public final class ValidationResult {

    public enum Status {
        /** Spec parsed successfully; check {@link #getViolations()} (may be empty). */
        SUCCESS,
        /** The input was not parseable as the declared {@link SpecFormat} at all. */
        PARSE_ERROR,
        /** The plugin/engine itself failed unexpectedly while validating. */
        PLUGIN_ERROR
    }

    private final Status status;
    private final List<Violation> violations;
    private final String errorMessage;
    private final int rulesEvaluatedCount;

    private ValidationResult(Builder b) {
        this.status = Objects.requireNonNull(b.status, "status must not be null");
        this.violations = b.violations == null ? Collections.emptyList() : List.copyOf(b.violations);
        this.errorMessage = b.errorMessage;
        this.rulesEvaluatedCount = b.rulesEvaluatedCount;
    }

    public Status getStatus() {
        return status;
    }

    /** Never null. Empty on PARSE_ERROR/PLUGIN_ERROR, or on a fully compliant spec. */
    public List<Violation> getViolations() {
        return violations;
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
     * present and fall back to "no violations found" when -1. A full
     * per-rule breakdown (which specific rules passed) was deliberately
     * left out of v1 — it roughly doubles the DTO surface for a feature
     * only worth building once a plugin author actually asks for it.
     */
    public int getRulesEvaluatedCount() {
        return rulesEvaluatedCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ValidationResult success(List<Violation> violations, int rulesEvaluatedCount) {
        return builder()
                .status(Status.SUCCESS)
                .violations(violations)
                .rulesEvaluatedCount(rulesEvaluatedCount)
                .build();
    }

    public static ValidationResult parseError(String message) {
        return builder().status(Status.PARSE_ERROR).errorMessage(message).build();
    }

    public static ValidationResult pluginError(String message) {
        return builder().status(Status.PLUGIN_ERROR).errorMessage(message).build();
    }

    public static final class Builder {
        private Status status;
        private List<Violation> violations;
        private String errorMessage;
        private int rulesEvaluatedCount = -1;

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder violations(List<Violation> violations) {
            this.violations = violations;
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

        public ValidationResult build() {
            return new ValidationResult(this);
        }
    }
}
