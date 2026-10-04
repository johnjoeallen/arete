package net.dublinux.arete.gradle;

import net.dublinux.arete.engine.BundleValidationException;
import net.dublinux.arete.engine.gate.GateException;
import net.dublinux.arete.engine.gate.GateJob;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

/**
 * The merge-gate: the specs a change touched, each judged against its base from git. Never up to date, because the
 * answer depends on the repository, not on any file Gradle tracks.
 */
public abstract class AreteGateTask extends DefaultTask {
    @Input @Optional public abstract Property<String> getPolicy();
    @Input public abstract ListProperty<String> getPolicySources();
    @Input public abstract ListProperty<String> getMavenRepositories();
    @Internal public abstract RegularFileProperty getMavenSettings();
    @Input public abstract Property<Boolean> getUseMavenSettings();
    @Input public abstract Property<Boolean> getRequirePin();
    @Internal public abstract DirectoryProperty getCacheDir();
    @Internal public abstract DirectoryProperty getUserPolicies();
    @Internal public abstract DirectoryProperty getRepository();
    @Input @Optional public abstract Property<String> getTarget();
    @Input @Optional public abstract Property<String> getBaseSha();
    @Input public abstract ListProperty<String> getPaths();
    @Input @Optional public abstract Property<String> getRawUrl();
    @Internal public abstract MapProperty<String, String> getRawHeaders();
    @Input public abstract Property<Boolean> getReportOnly();
    @Internal public abstract DirectoryProperty getReportDirectory();

    public AreteGateTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void gate() {
        GateJob.Config config = JobConfig.gate(
                JobConfig.policy(getPolicy().getOrNull(), getPolicySources().get(), getMavenRepositories().get(),
                        path(getMavenSettings().getAsFile().getOrNull()), getUseMavenSettings().get(), getRequirePin().get(),
                        path(getCacheDir().getAsFile().getOrNull()), path(getUserPolicies().getAsFile().getOrNull())),
                getRepository().get().getAsFile().toPath(), getTarget().getOrNull(), getBaseSha().getOrNull(), getPaths().get(),
                getRawUrl().getOrNull(), getRawHeaders().get(), getReportDirectory().get().getAsFile().toPath(), getReportOnly().get());

        GateJob.Outcome outcome;
        try {
            outcome = GateJob.run(config);
        } catch (BundleValidationException | GateException e) {
            // A configuration problem (a base that cannot be reached, a policy that fails its pin), not a verdict.
            throw new GradleException(e.getMessage(), e);
        }
        String report = outcome.text();
        if (outcome.result().passed()) getLogger().lifecycle(report);
        else getLogger().warn(report);
        if (outcome.engineError()) throw new GradleException("a spec could not be scored:\n" + report);
        if (outcome.blocks()) throw new GradleException("The Areté gate failed:\n" + String.join("\n", outcome.result().reasons()));
    }

    private static java.nio.file.Path path(File file) {
        return file == null ? null : file.toPath();
    }
}
