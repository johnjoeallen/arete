package net.dublinux.arete.engine.api;

import java.util.Objects;

/**
 * The spec to score: its raw text plus the context the engine needs.
 *
 * <p>{@code content} is always a single document's raw text (YAML or JSON). Multi-file specs are not
 * supported yet.
 *
 * <p>{@code baseUri} is optional: it is used to resolve relative {@code $ref}s and as a filename in
 * reports.
 *
 * <p>{@code policy} names one of the policies the engine offers; it defaults to {@link #DEFAULT_POLICY},
 * which the engine reads as "its default".
 */
public final class SpecInput {
    /** The policy used when a caller names none. */
    public static final String DEFAULT_POLICY = "default";


    private final String content;
    private final SpecFormat format;
    private final String baseUri;
    private final String policy;

    private SpecInput(Builder b) {
        this.content = Objects.requireNonNull(b.content, "content must not be null");
        this.format = Objects.requireNonNull(b.format, "format must not be null");
        this.baseUri = b.baseUri;
        this.policy = Objects.requireNonNull(b.policy, "policy must not be null");
    }

    public String getContent() {
        return content;
    }

    public SpecFormat getFormat() {
        return format;
    }

    /** Nullable. */
    public String getBaseUri() {
        return baseUri;
    }

    /** Never null; see the class-level doc. */
    public String getPolicy() {
        return policy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;
        private SpecFormat format;
        private String baseUri;
        private String policy = DEFAULT_POLICY;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder format(SpecFormat format) {
            this.format = format;
            return this;
        }

        public Builder baseUri(String baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        public Builder policy(String policy) {
            this.policy = policy;
            return this;
        }

        public SpecInput build() {
            return new SpecInput(this);
        }
    }
}
