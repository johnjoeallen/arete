package net.dublinux.arete.maven;

import net.dublinux.arete.engine.BundleValidationException;
import net.dublinux.arete.engine.gate.GateException;
import net.dublinux.arete.engine.gate.GateJob;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * The merge-gate. Finds the specs a change touched, reads each one's base from git, scores both with the same policy
 * and judges the difference: a changed spec fails on a new blocker or a lower score; a new spec must meet the policy's
 * pass mark. Runs once, at the directory Maven was started in, and writes {@code gate.json}, {@code gate.md} and
 * {@code gate.sarif} for a later CI step to post.
 *
 * <p>Bind it where you like (it defaults to {@code verify}) or run {@code mvn net.dublinux.arete:arete-maven-plugin:gate}.
 */
@Mojo(name = "gate", defaultPhase = LifecyclePhase.VERIFY, aggregator = true, requiresProject = false, threadSafe = true)
public class GateMojo extends AbstractAreteMojo {
    /** The git repository to check. */
    @Parameter(property = "arete.repository", defaultValue = "${session.executionRootDirectory}")
    File repository;

    /** The branch the change will merge into; the base is the merge-base of HEAD and it. */
    @Parameter(property = "arete.target", defaultValue = "origin/main")
    String target;

    /** The base commit itself (GitLab's CI_MERGE_REQUEST_DIFF_BASE_SHA, say), instead of the merge-base. */
    @Parameter(property = "arete.baseSha")
    String baseSha;

    /** Globs for the spec files, relative to the repository. Default {@code **}{@code /openapi.yaml}, {@code .yml}, {@code .json}. */
    @Parameter
    List<String> paths;

    /** Read the base over HTTPS from the code host instead of git (a clone too shallow to hold it): a URL with {path} and {ref}. Needs baseSha. */
    @Parameter(property = "arete.rawUrl")
    String rawUrl;

    /** Headers for {@code rawUrl}, such as {@code PRIVATE-TOKEN}. Put the secret in a Maven property or an environment variable, not the pom. */
    @Parameter
    Map<String, String> rawHeaders;

    /** Where {@code gate.json}, {@code gate.md} and {@code gate.sarif} are written. */
    @Parameter(property = "arete.reportDirectory", defaultValue = "${session.executionRootDirectory}/target/arete")
    File reportDirectory;

    /** Write the reports and never fail the build: a trial before the job is required. */
    @Parameter(property = "arete.reportOnly", defaultValue = "false")
    boolean reportOnly;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Areté gate skipped.");
            return;
        }
        GateJob.Outcome outcome;
        try {
            outcome = GateJob.run(config());
        } catch (BundleValidationException | GateException e) {
            // A configuration problem (a base that cannot be reached, a policy that fails its pin): not a verdict.
            throw new MojoExecutionException(e.getMessage(), e);
        }
        String report = outcome.text();
        if (outcome.result().passed()) getLog().info("\n" + report);
        else getLog().warn("\n" + report);
        if (outcome.engineError()) throw new MojoExecutionException("a spec could not be scored:\n" + report);
        if (outcome.blocks()) {
            throw new MojoFailureException("The Areté gate failed:\n" + String.join("\n", outcome.result().reasons()));
        }
    }

    GateJob.Config config() {
        GateJob.Config config = policyConfig();
        config.repository = repository.toPath();
        config.target = target;
        config.baseSha = baseSha;
        if (paths != null) config.globs.addAll(paths);
        if (rawUrl != null) {
            config.rawUrl = rawUrl;
            if (rawHeaders != null) config.rawHeaders.putAll(rawHeaders);
        }
        config.reportJson = reportDirectory.toPath().resolve("gate.json");
        config.reportMarkdown = reportDirectory.toPath().resolve("gate.md");
        config.reportSarif = reportDirectory.toPath().resolve("gate.sarif");
        config.reportOnly = reportOnly;
        return config;
    }
}
