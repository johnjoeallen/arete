package net.dublinux.arete.maven;

import net.dublinux.arete.engine.gate.GateJob;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The policy settings every goal shares. A goal maps its Maven parameters onto a {@link GateJob.Config} and hands it to
 * the engine; nothing here scores anything.
 */
abstract class AbstractAreteMojo extends AbstractMojo {
    @Parameter(defaultValue = "${session}", readonly = true)
    MavenSession session;

    /** Skip the goal. */
    @Parameter(property = "arete.skip", defaultValue = "false")
    boolean skip;

    /** The policy to score with. Default: the one the spec's {@code .arete.yaml} names, else the bundle's first. */
    @Parameter(property = "arete.policy")
    String policy;

    /**
     * Policy bundles, layered in order: {@code classpath:api-policy} (the public bundle, the default), {@code file:...},
     * {@code https://...zip}, or {@code maven:group:artifact:version}, each with an optional
     * {@code #sha256=<hex>&version=<v>} pin.
     */
    @Parameter
    List<String> policySources;

    /** Extra Maven-layout repositories for {@code maven:} policy sources, tried before the ones in settings.xml. */
    @Parameter
    List<String> mavenRepositories;

    /**
     * Read repositories, mirrors, credentials and proxies from the settings.xml this build was started with (so
     * {@code mvn -s ...} and the active profiles apply). On by default: CI already says there where artifacts live.
     */
    @Parameter(property = "arete.useMavenSettings", defaultValue = "true")
    boolean useMavenSettings;

    /** Refuse a remote policy source with no {@code sha256} pin. */
    @Parameter(property = "arete.requirePin", defaultValue = "false")
    boolean requirePin;

    /** Where fetched policy bundles are kept (default {@code ~/.arete/cache/policies}). */
    @Parameter(property = "arete.cacheDir")
    File cacheDir;

    /** A directory of extra {@code *.md} policies. */
    @Parameter(property = "arete.userPolicies")
    File userPolicies;

    /** Fills in the policy settings; a goal adds its own. */
    GateJob.Config policyConfig() {
        GateJob.Config config = new GateJob.Config();
        config.policy = policy;
        if (policySources != null) config.policySources.addAll(policySources);
        if (mavenRepositories != null) config.mavenRepositories.addAll(mavenRepositories);
        config.requirePin = requirePin;
        if (cacheDir != null) config.cacheDir = cacheDir.toPath();
        if (userPolicies != null) config.userPoliciesDir = userPolicies.toPath();
        if (useMavenSettings) {
            if (session != null) {
                List<Path> files = new ArrayList<>();
                addIfPresent(files, session.getRequest().getGlobalSettingsFile());
                addIfPresent(files, session.getRequest().getUserSettingsFile());
                config.mavenSettingsFiles.addAll(files);
                config.mavenProfiles.addAll(session.getRequest().getActiveProfiles());
                if (files.isEmpty()) config.useDefaultMavenSettings = true;
            } else {
                config.useDefaultMavenSettings = true;
            }
        }
        return config;
    }

    private static void addIfPresent(List<Path> files, File file) {
        if (file != null && file.isFile()) files.add(file.toPath());
    }
}
