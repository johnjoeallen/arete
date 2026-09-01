package net.dublinux.arete.cigate.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers the {@code areteCiGate { }} extension and the
 * {@code areteCiGateCheck} task, and wires it into {@code check}.
 */
public class AreteCiGatePlugin implements Plugin<Project> {

    static final String EXTENSION = "areteCiGate";
    static final String TASK = "areteCiGateCheck";

    @Override
    public void apply(Project project) {
        AreteCiGateExtension ext = project.getExtensions()
                .create(EXTENSION, AreteCiGateExtension.class);

        ext.getUrl().convention(
                project.getProviders().gradleProperty("arete.url").orElse("http://localhost:6809"));
        ext.getNamespace().convention(project.provider(() -> String.valueOf(project.getGroup())));
        ext.getSarif().convention(false);
        ext.getFailOnUnavailable().convention(true);

        project.getTasks().register(TASK, AreteCiGateCheckTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Submits the OpenAPI spec to Areté and fails on a policy failure.");

            task.getUrl().set(ext.getUrl());
            task.getNamespace().set(ext.getNamespace());
            task.getSubmitter().set(ext.getSubmitter());
            task.getSarif().set(ext.getSarif());
            task.getFailOn().set(ext.getFailOn());
            task.getFailOnUnavailable().set(ext.getFailOnUnavailable());

            task.getSpecFiles().from(project.provider(() -> specFiles(ext)));

            task.getCombinationRuns().set(ext.getCombinations().map(list -> {
                List<String> runs = new ArrayList<>();
                list.forEach(c -> runs.add(c.getRun()));
                return runs;
            }));
            task.getCombinationOptional().set(ext.getCombinations().map(list -> {
                List<Boolean> flags = new ArrayList<>();
                list.forEach(c -> flags.add(c.isOptional()));
                return flags;
            }));

            task.getReportDir().set(project.getLayout().getBuildDirectory().dir("reports/arete-ci-gate"));
        });

        project.getPluginManager().withPlugin("lifecycle-base", applied ->
                project.getTasks().named("check", t -> t.dependsOn(TASK)));
    }

    private static List<File> specFiles(AreteCiGateExtension ext) {
        List<File> files = new ArrayList<>(ext.getSpecs().getOrElse(List.of()));
        File single = ext.getSpec().getOrNull();
        if (single != null) {
            files.add(single);
        }
        return files;
    }
}
