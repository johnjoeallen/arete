package net.dublinux.arete.engine.gate;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.report.ReportWriter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a front end does for the gate, in one place: build the engine from its policy settings, run the gate over a
 * repository, and write the report files. The command line and the Maven and Gradle plugins only fill in a
 * {@link Config} and act on the {@link Outcome}, so they cannot drift apart.
 */
public final class GateJob {
    private GateJob() { }

    /** Settings for one run. Every list and map is optional; nothing is read from the user's home directory unless asked. */
    public static final class Config {
        public Path repository = Path.of("");
        // policy
        public final List<String> policySources = new ArrayList<>();
        public final List<String> mavenRepositories = new ArrayList<>();
        public final List<Path> mavenSettingsFiles = new ArrayList<>();
        public boolean useDefaultMavenSettings;
        public final List<String> mavenProfiles = new ArrayList<>();
        public boolean requirePin;
        public Path cacheDir;
        public boolean noCache;
        public Path userPoliciesDir;
        public String policy;
        // gate
        public String target;
        public String baseSha;
        public final List<String> globs = new ArrayList<>();
        public String rawUrl;
        public final Map<String, String> rawHeaders = new LinkedHashMap<>();
        public List<String> changedFiles;
        public HttpClient httpClient;
        // outputs
        public Path reportJson;
        public Path reportMarkdown;
        public Path reportSarif;
        public boolean reportOnly;
    }

    /** What happened: the verdict, and whether it should stop the build. */
    public record Outcome(GateResult result, boolean engineError, boolean blocks) {
        /** The verdict as text, for a build log. */
        public String text() { return ReportWriter.text(result); }
    }

    /** Builds the engine from the policy settings in {@code config}: sources, repositories, Maven settings, pins and cache. */
    public static Engine engine(Config config) {
        Engine.Builder builder = Engine.builder();
        for (String source : config.policySources) builder.policySource(source);
        for (String repository : config.mavenRepositories) builder.mavenRepository(repository);
        if (!config.mavenSettingsFiles.isEmpty()) builder.mavenSettings(config.mavenSettingsFiles);
        else if (config.useDefaultMavenSettings) builder.mavenSettings();
        for (String profile : config.mavenProfiles) builder.mavenProfile(profile);
        builder.requirePin(config.requirePin);
        if (config.noCache) builder.cacheDir(null);
        else if (config.cacheDir != null) builder.cacheDir(config.cacheDir);
        if (config.userPoliciesDir != null) builder.userPoliciesDir(config.userPoliciesDir);
        return builder.build();
    }

    /** Builds the engine, runs the gate, writes the report files. A policy source or base that cannot be used throws. */
    public static Outcome run(Config config) {
        Engine engine = engine(config);
        GateRequest.Builder request = GateRequest.builder();
        if (config.target != null) request.target(config.target);
        if (config.baseSha != null) request.baseSha(config.baseSha);
        for (String glob : config.globs) request.glob(glob);
        if (config.policy != null) request.policy(config.policy);
        if (config.rawUrl != null) {
            request.raw(config.rawUrl, config.rawHeaders);
            if (config.changedFiles != null) request.changedFiles(config.changedFiles);
        }
        if (config.httpClient != null) request.httpClient(config.httpClient);
        GateResult result = GateRunner.run(engine, config.repository, request.build());

        write(config.reportJson, ReportWriter.json(result));
        write(config.reportMarkdown, ReportWriter.markdown(result));
        write(config.reportSarif, ReportWriter.sarif(result));
        boolean engineError = result.hasEngineError();
        return new Outcome(result, engineError, !engineError && !result.passed() && !config.reportOnly);
    }

    private static void write(Path file, String content) {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GateException("could not write " + file + ": " + e.getMessage());
        }
    }
}
