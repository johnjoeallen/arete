package net.dublinux.arete.maven;

import net.dublinux.arete.engine.BundleValidationException;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.gate.GateJob;
import net.dublinux.arete.engine.report.ReportWriter;
import net.dublinux.arete.engine.report.ScoreReport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scores the given spec files and fails the build when one is under the bar. The right goal for a module that owns one
 * API; for a repository of many, {@code gate} judges only what a change touched. The nearest {@code .arete.yaml} next to
 * each spec (up to the module's directory) is applied.
 */
@Mojo(name = "score", defaultPhase = LifecyclePhase.VERIFY, requiresProject = false, threadSafe = true)
public class ScoreMojo extends AbstractAreteMojo {
    /** The spec files to score. */
    @Parameter(property = "arete.specs", required = true)
    List<File> specs;

    /** The base directory {@code .arete.yaml} files are looked for up to. */
    @Parameter(property = "arete.repository", defaultValue = "${session.executionRootDirectory}")
    File repository;

    /** A score the spec must reach, or {@code policy} for the policy's own pass mark. Omit to only report. */
    @Parameter(property = "arete.failUnder")
    String failUnder;

    /** Where {@code score.json} and {@code score.md} are written. */
    @Parameter(property = "arete.reportDirectory", defaultValue = "${session.executionRootDirectory}/target/arete")
    File reportDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Areté score skipped.");
            return;
        }
        Double bar = null;
        if (failUnder != null && !failUnder.equals("policy")) {
            try {
                bar = Double.parseDouble(failUnder);
            } catch (NumberFormatException e) {
                throw new MojoExecutionException("failUnder must be a number or 'policy': " + failUnder);
            }
        }
        List<ScoreReport> reports = new ArrayList<>();
        try {
            Engine engine = GateJob.engine(policyConfig());
            Path root = repository.toPath();
            for (File spec : specs) {
                String text = Files.readString(spec.toPath(), StandardCharsets.UTF_8);
                Overrides overrides = Overrides.discover(spec.toPath(), root);
                reports.add(ScoreReport.of(engine, root.relativize(spec.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/'), text,
                        policy != null ? policy : overrides.policy(), overrides));
            }
            Files.createDirectories(reportDirectory.toPath());
            Files.writeString(reportDirectory.toPath().resolve("score.json"), ReportWriter.json(reports), StandardCharsets.UTF_8);
            Files.writeString(reportDirectory.toPath().resolve("score.md"), ReportWriter.markdown(reports, true), StandardCharsets.UTF_8);
        } catch (BundleValidationException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        getLog().info("\n" + ReportWriter.text(reports));
        List<String> failures = new ArrayList<>();
        for (ScoreReport report : reports) {
            if (report.status().equals("ENGINE_ERROR")) throw new MojoExecutionException(report.file() + " could not be scored: " + report.errorMessage());
            if (!report.succeeded()) failures.add(report.file() + ": " + report.status());
            else if (bar != null && report.score() < bar) failures.add(report.file() + " scores " + report.score() + ", under " + bar);
            else if ("policy".equals(failUnder) && !report.meetsPassingScore()) failures.add(report.file() + " scores " + report.score() + ", under the " + report.policy() + " pass mark of " + report.passingScore());
        }
        if (!failures.isEmpty()) throw new MojoFailureException("Areté score failed:\n" + String.join("\n", failures));
    }
}
