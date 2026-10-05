package net.dublinux.arete.gradle;

import net.dublinux.arete.engine.gate.GateJob;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Maps the plain values of the extension onto the engine's job configuration. Kept free of Gradle types so it can be tested. */
final class JobConfig {
    private JobConfig() { }

    /** The policy settings both tasks share. */
    static GateJob.Config policy(String policy, List<String> policySources, List<String> mavenRepositories, Path mavenSettings,
            boolean useMavenSettings, boolean requirePin, Path cacheDir, Path userPolicies) {
        GateJob.Config config = new GateJob.Config();
        config.policy = policy;
        config.policySources.addAll(policySources);
        config.mavenRepositories.addAll(mavenRepositories);
        if (mavenSettings != null) config.mavenSettingsFiles.add(mavenSettings);
        else config.useDefaultMavenSettings = useMavenSettings;
        config.requirePin = requirePin;
        config.cacheDir = cacheDir;
        config.userPoliciesDir = userPolicies;
        return config;
    }

    static GateJob.Config gate(GateJob.Config config, Path repository, String target, String baseSha, List<String> paths,
            String rawUrl, Map<String, String> rawHeaders, Path reportDirectory, boolean reportOnly) {
        config.repository = repository;
        config.target = target;
        config.baseSha = baseSha;
        config.globs.addAll(paths);
        if (rawUrl != null) {
            config.rawUrl = rawUrl;
            config.rawHeaders.putAll(rawHeaders);
        }
        config.reportJson = reportDirectory.resolve("gate.json");
        config.reportMarkdown = reportDirectory.resolve("gate.md");
        config.reportSarif = reportDirectory.resolve("gate.sarif");
        config.reportOnly = reportOnly;
        return config;
    }
}
