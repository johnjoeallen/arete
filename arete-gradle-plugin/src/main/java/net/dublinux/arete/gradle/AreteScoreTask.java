package net.dublinux.arete.gradle;

import net.dublinux.arete.engine.BundleValidationException;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.gate.GateJob;
import net.dublinux.arete.engine.report.ReportWriter;
import net.dublinux.arete.engine.report.ScoreReport;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Scores the given spec files and fails the build when one is under the bar. */
public abstract class AreteScoreTask extends DefaultTask {
    @Input @Optional public abstract Property<String> getPolicy();
    @Input public abstract ListProperty<String> getPolicySources();
    @Input public abstract ListProperty<String> getMavenRepositories();
    @Internal public abstract RegularFileProperty getMavenSettings();
    @Input public abstract Property<Boolean> getUseMavenSettings();
    @Input public abstract Property<Boolean> getRequirePin();
    @Internal public abstract DirectoryProperty getCacheDir();
    @Internal public abstract DirectoryProperty getUserPolicies();
    @Internal public abstract DirectoryProperty getRepository();
    @InputFiles public abstract ConfigurableFileCollection getSpecs();
    @Input @Optional public abstract Property<String> getFailUnder();
    @OutputDirectory public abstract DirectoryProperty getReportDirectory();

    @TaskAction
    public void score() {
        String failUnder = getFailUnder().getOrNull();
        Double bar = null;
        if (failUnder != null && !failUnder.equals("policy")) {
            try {
                bar = Double.parseDouble(failUnder);
            } catch (NumberFormatException e) {
                throw new GradleException("failUnder must be a number or 'policy': " + failUnder);
            }
        }
        GateJob.Config config = JobConfig.policy(getPolicy().getOrNull(), getPolicySources().get(), getMavenRepositories().get(),
                path(getMavenSettings().getAsFile().getOrNull()), getUseMavenSettings().get(), getRequirePin().get(),
                path(getCacheDir().getAsFile().getOrNull()), path(getUserPolicies().getAsFile().getOrNull()));
        List<ScoreReport> reports = new ArrayList<>();
        try {
            Engine engine = GateJob.engine(config);
            Path root = getRepository().get().getAsFile().toPath();
            for (File spec : getSpecs().getFiles()) {
                String text = Files.readString(spec.toPath(), StandardCharsets.UTF_8);
                Overrides overrides = Overrides.discover(spec.toPath(), root);
                String label = root.relativize(spec.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
                reports.add(ScoreReport.of(engine, label, text, getPolicy().getOrNull() != null ? getPolicy().get() : overrides.policy(), overrides));
            }
            Path out = getReportDirectory().get().getAsFile().toPath();
            Files.createDirectories(out);
            Files.writeString(out.resolve("score.json"), ReportWriter.json(reports), StandardCharsets.UTF_8);
            Files.writeString(out.resolve("score.md"), ReportWriter.markdown(reports, true), StandardCharsets.UTF_8);
        } catch (BundleValidationException | IOException e) {
            throw new GradleException(e.getMessage(), e);
        }
        getLogger().lifecycle(ReportWriter.text(reports));
        List<String> failures = new ArrayList<>();
        for (ScoreReport report : reports) {
            if (report.status().equals("PLUGIN_ERROR")) throw new GradleException(report.file() + " could not be scored: " + report.errorMessage());
            if (!report.succeeded()) failures.add(report.file() + ": " + report.status());
            else if (bar != null && report.score() < bar) failures.add(report.file() + " scores " + report.score() + ", under " + bar);
            else if ("policy".equals(failUnder) && !report.meetsPassingScore()) failures.add(report.file() + " scores " + report.score() + ", under the " + report.policy() + " pass mark of " + report.passingScore());
        }
        if (!failures.isEmpty()) throw new GradleException("Areté score failed:\n" + String.join("\n", failures));
    }

    private static Path path(File file) {
        return file == null ? null : file.toPath();
    }
}
