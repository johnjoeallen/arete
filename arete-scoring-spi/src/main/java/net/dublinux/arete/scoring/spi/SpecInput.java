package net.dublinux.arete.scoring.spi;

import java.util.Objects;

/**
 * The spec to validate, passed as raw text plus routing/context metadata.
 *
 * <p>Open question #1 (batch / multi-file specs): out of scope for v1.
 * {@code content} is always a single document's raw text. Multi-file specs
 * with cross-file {@code $ref}s would require either (a) a shared parsed
 * model across the classloader boundary — which constraint #4 explicitly
 * rules out — or (b) a bundling scheme (e.g. a Map&lt;String, String&gt; of
 * filename to content) that every plugin would need to resolve itself. The
 * latter is plausible as a v2 addition ({@code Map<String,String> auxiliaryFiles})
 * but was left out to keep the v1 surface minimal per constraint #5; add it
 * as a new field with a builder default of an empty map if/when a real
 * multi-file use case shows up, which is backward compatible for existing
 * plugin jars built against the builder.
 *
 * <p>{@code baseUri} is optional and used by plugins that need to resolve
 * relative {@code $ref}s or want a filename for reporting purposes.
 *
 * <p>{@code policy} is one of the values the plugin itself declared via
 * {@link SpecScoringPlugin#getPolicies()} (or {@link
 * SpecScoringPlugin#DEFAULT_POLICY} if nothing was explicitly
 * selected) — see that method's javadoc for the full contract, including
 * why this is a plain name rather than a typed concept. Defaults to
 * {@code DEFAULT_POLICY} so existing callers that never set it (host code
 * predating this concept, or a test) keep working unchanged.
 */
public final class SpecInput {

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
        private String policy = SpecScoringPlugin.DEFAULT_POLICY;

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
