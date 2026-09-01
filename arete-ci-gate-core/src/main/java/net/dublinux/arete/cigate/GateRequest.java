package net.dublinux.arete.cigate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything one spec submission needs: where Areté is, who is asking, the
 * spec bytes, and which combinations to run. Build with {@link #builder()}.
 */
public final class GateRequest {

    private final String areteBaseUrl;
    private final String namespace;
    private final String submitter;
    private final byte[] specBytes;
    private final String specContentType;
    private final String specDisplayName;
    private final List<Combination> combinations;
    private final boolean sarif;
    private final String failOn;

    private GateRequest(Builder b) {
        this.areteBaseUrl = stripTrailingSlash(require(b.areteBaseUrl, "areteBaseUrl"));
        this.namespace = require(b.namespace, "namespace");
        this.submitter = require(b.submitter, "submitter");
        this.specBytes = Objects.requireNonNull(b.specBytes, "specBytes").clone();
        this.specContentType = require(b.specContentType, "specContentType");
        this.specDisplayName = b.specDisplayName == null ? b.namespace : b.specDisplayName;
        if (b.combinations.isEmpty()) {
            throw new IllegalArgumentException("at least one combination is required");
        }
        this.combinations = List.copyOf(b.combinations);
        this.sarif = b.sarif;
        this.failOn = b.failOn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String areteBaseUrl() {
        return areteBaseUrl;
    }

    public String namespace() {
        return namespace;
    }

    public String submitter() {
        return submitter;
    }

    public byte[] specBytes() {
        return specBytes.clone();
    }

    public String specContentType() {
        return specContentType;
    }

    public String specDisplayName() {
        return specDisplayName;
    }

    public List<Combination> combinations() {
        return combinations;
    }

    public boolean sarif() {
        return sarif;
    }

    /** {@code null} means "let each policy decide" — the plugin sends no {@code failOn}. */
    public String failOn() {
        return failOn;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {
        private String areteBaseUrl;
        private String namespace;
        private String submitter;
        private byte[] specBytes;
        private String specContentType = "application/yaml";
        private String specDisplayName;
        private final List<Combination> combinations = new ArrayList<>();
        private boolean sarif;
        private String failOn;

        public Builder areteBaseUrl(String v) {
            this.areteBaseUrl = v;
            return this;
        }

        public Builder namespace(String v) {
            this.namespace = v;
            return this;
        }

        public Builder submitter(String v) {
            this.submitter = v;
            return this;
        }

        public Builder spec(byte[] bytes, String contentType) {
            this.specBytes = bytes;
            this.specContentType = contentType;
            return this;
        }

        public Builder spec(String text) {
            this.specBytes = text.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder specContentType(String v) {
            this.specContentType = v;
            return this;
        }

        public Builder specDisplayName(String v) {
            this.specDisplayName = v;
            return this;
        }

        public Builder combination(Combination c) {
            this.combinations.add(c);
            return this;
        }

        public Builder combinations(List<Combination> c) {
            this.combinations.addAll(c);
            return this;
        }

        public Builder sarif(boolean v) {
            this.sarif = v;
            return this;
        }

        public Builder failOn(String v) {
            this.failOn = v == null || v.isBlank() ? null : v.trim();
            return this;
        }

        public GateRequest build() {
            return new GateRequest(this);
        }
    }
}
