package net.dublinux.arete.cigate.gradle;

import net.dublinux.arete.cigate.AreteGateClient;
import net.dublinux.arete.cigate.AreteGateException;
import net.dublinux.arete.cigate.Combination;
import net.dublinux.arete.cigate.GateOutcome;
import net.dublinux.arete.cigate.GateReport;
import net.dublinux.arete.cigate.GateRequest;
import net.dublinux.arete.cigate.Submitters;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Submits the spec(s) to Areté and fails the build on a non-optional policy failure. */
public abstract class AreteCiGateCheckTask extends DefaultTask {

    @Input
    public abstract Property<String> getUrl();

    @Input
    public abstract Property<String> getNamespace();

    @Input
    @Optional
    public abstract Property<String> getSubmitter();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSpecFiles();

    @Input
    public abstract ListProperty<String> getCombinationRuns();

    @Input
    public abstract ListProperty<Boolean> getCombinationOptional();

    @Input
    public abstract Property<Boolean> getSarif();

    @Input
    @Optional
    public abstract Property<String> getFailOn();

    @Input
    public abstract Property<Boolean> getFailOnUnavailable();

    @OutputDirectory
    public abstract org.gradle.api.file.DirectoryProperty getReportDir();

    @TaskAction
    public void run() {
        List<File> specs = new ArrayList<>(getSpecFiles().getFiles());
        if (specs.isEmpty()) {
            throw new GradleException("areteCiGate: no spec file configured. Set `spec` or `specs`.");
        }
        List<Combination> combos = combinations();
        if (combos.isEmpty()) {
            throw new GradleException("areteCiGate: at least one combination(\"validator/policy\") is required.");
        }

        String who = Submitters.resolve(getSubmitter().getOrNull(), "gradle", System::getenv);
        AreteGateClient client = new AreteGateClient();
        Path reportDir = getReportDir().get().getAsFile().toPath();

        boolean anyFailure = false;
        for (File specFile : specs) {
            GateRequest request = GateRequest.builder()
                    .areteBaseUrl(getUrl().get())
                    .namespace(getNamespace().get())
                    .submitter(who)
                    .spec(read(specFile), contentTypeOf(specFile))
                    .specDisplayName(getProject().getName() + " (" + specFile.getName() + ")")
                    .combinations(combos)
                    .sarif(getSarif().getOrElse(false))
                    .failOn(getFailOn().getOrNull())
                    .build();

            GateOutcome outcome;
            try {
                outcome = client.submitAndScore(request);
            } catch (AreteGateException e) {
                if (!getFailOnUnavailable().getOrElse(true) && e.httpStatus() == null) {
                    getLogger().warn("areteCiGate: {} — not failing the build (failOnUnavailable=false)", e.getMessage());
                    continue;
                }
                throw new GradleException("areteCiGate: " + e.getMessage(), e);
            }

            String report = GateReport.render(request, outcome);
            getLogger().lifecycle("\n{}", report);
            writeReports(reportDir, specFile, report, outcome);

            if (!outcome.buildPassed()) {
                anyFailure = true;
            }
        }

        if (anyFailure) {
            throw new VerificationException("Areté CI Gate: a non-optional combination failed its policy. "
                    + "See " + reportDir.resolve("report.txt"));
        }
    }

    private List<Combination> combinations() {
        List<String> runs = getCombinationRuns().get();
        List<Boolean> optional = getCombinationOptional().get();
        List<Combination> combos = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            combos.add(Combination.parse(runs.get(i), i < optional.size() && optional.get(i)));
        }
        return combos;
    }

    private static byte[] read(File f) {
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            throw new GradleException("areteCiGate: could not read " + f, e);
        }
    }

    private static String contentTypeOf(File f) {
        return f.getName().toLowerCase().endsWith(".json") ? "application/json" : "application/yaml";
    }

    private void writeReports(Path dir, File specFile, String report, GateOutcome outcome) {
        try {
            Files.createDirectories(dir);
            String base = specFile.getName().replaceFirst("\\.[^.]+$", "");
            Files.writeString(dir.resolve("report.txt"), report, StandardCharsets.UTF_8);
            if (outcome.sarif() != null) {
                Files.writeString(dir.resolve(base + ".sarif"), outcome.sarif(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLogger().warn("areteCiGate: could not write report to {}: {}", dir, e.getMessage());
        }
    }
}
