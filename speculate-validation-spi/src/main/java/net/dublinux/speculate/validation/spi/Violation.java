package net.dublinux.speculate.validation.spi;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single rule violation found in a spec.
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
 */
public final class Violation {

    private final String ruleId;
    private final String title;
    private final String description;
    private final Severity severity;
    private final String pointer;
    private final List<String> paths;
    private final Integer lineNumber;
    private final String documentationUrl;

    private Violation(Builder b) {
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId must not be null");
        this.title = Objects.requireNonNull(b.title, "title must not be null");
        this.description = b.description;
        this.severity = Objects.requireNonNull(b.severity, "severity must not be null");
        this.pointer = b.pointer;
        this.paths = b.paths == null ? Collections.emptyList() : List.copyOf(b.paths);
        this.lineNumber = b.lineNumber;
        this.documentationUrl = b.documentationUrl;
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

    /** Affected API paths/operations; never null, may be empty. */
    public List<String> getPaths() {
        return paths;
    }

    /**
     * 1-based line number of the violation within the raw spec text passed
     * in {@link SpecInput#getContent()}, for editors/UIs that want to jump
     * straight to the offending line. Nullable — not every engine tracks
     * source positions (some work purely against the parsed object graph),
     * and even engines that do may not report a line for every violation
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
     * next to the violation. Nullable — not every rule has published docs.
     */
    public String getDocumentationUrl() {
        return documentationUrl;
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

        public Violation build() {
            return new Violation(this);
        }
    }
}
