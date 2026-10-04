package net.dublinux.arete.engine.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the gate is asked to do: against what base, over which specs, as which policy. */
public final class GateRequest {
    /** Where the changed specs and their base come from. */
    public enum Mode { GIT, RAW }

    final Mode mode;
    final String target;
    final String baseSha;
    final List<String> globs;
    final String policy;
    final String rawUrl;
    final Map<String, String> rawHeaders;
    final List<String> changedFiles;
    final java.net.http.HttpClient http;

    private GateRequest(Builder b) {
        this.mode = b.mode;
        this.target = b.target;
        this.baseSha = b.baseSha;
        this.globs = List.copyOf(b.globs.isEmpty() ? DEFAULT_GLOBS : b.globs);
        this.policy = b.policy;
        this.rawUrl = b.rawUrl;
        this.rawHeaders = Map.copyOf(b.rawHeaders);
        this.changedFiles = b.changedFiles == null ? null : List.copyOf(b.changedFiles);
        this.http = b.http;
    }

    /** The spec files looked at when no globs are given. */
    public static final List<String> DEFAULT_GLOBS = List.of("**/openapi.yaml", "**/openapi.yml", "**/openapi.json");

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Mode mode = Mode.GIT;
        private String target = "origin/main";
        private String baseSha;
        private final List<String> globs = new ArrayList<>();
        private String policy;
        private String rawUrl;
        private final Map<String, String> rawHeaders = new LinkedHashMap<>();
        private List<String> changedFiles;
        private java.net.http.HttpClient http;

        /** The branch the change will merge into; the base is the merge-base of HEAD and it. Default {@code origin/main}. */
        public Builder target(String ref) { this.target = ref; return this; }

        /** The base commit itself (a CI variable such as GitLab's CI_MERGE_REQUEST_DIFF_BASE_SHA), instead of computing the merge-base. */
        public Builder baseSha(String sha) { this.baseSha = sha; return this; }

        /** A glob for the spec files to look at, relative to the repository root; repeatable. */
        public Builder glob(String glob) { this.globs.add(glob); return this; }

        /** The policy to score with; null takes the one the spec's .arete.yaml names, else the engine's default. */
        public Builder policy(String name) { this.policy = name; return this; }

        /** Read the base over HTTPS from the code host instead of from git: for a clone too shallow to hold it. Needs {@link #baseSha}. */
        public Builder raw(String urlTemplate, Map<String, String> headers) {
            this.mode = Mode.RAW;
            this.rawUrl = urlTemplate;
            this.rawHeaders.putAll(headers);
            return this;
        }

        /** In raw mode, the files to consider, instead of every file matching the globs. */
        public Builder changedFiles(List<String> files) { this.changedFiles = files; return this; }

        public Builder httpClient(java.net.http.HttpClient client) { this.http = client; return this; }

        public GateRequest build() {
            if (mode == Mode.RAW && (baseSha == null || baseSha.isBlank())) throw new GateException("raw mode needs the base commit (baseSha): without git there is no merge-base to compute");
            return new GateRequest(this);
        }
    }
}
