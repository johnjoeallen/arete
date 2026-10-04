package net.dublinux.arete.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * {@code plugins { id 'net.dublinux.arete' }}: an {@code arete { }} block and two tasks, {@code areteGate} (the merge-gate)
 * and {@code areteScore}. Neither runs with {@code check}: run the gate as its own CI job so it can be required.
 */
public class AretePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AreteExtension arete = project.getExtensions().create("arete", AreteExtension.class);
        arete.getRequirePin().convention(false);
        arete.getUseMavenSettings().convention(false);
        arete.getReportOnly().convention(false);
        arete.getRepository().convention(project.getRootProject().getLayout().getProjectDirectory());
        arete.getReportDirectory().convention(project.getLayout().getBuildDirectory().dir("reports/arete"));

        project.getTasks().register("areteGate", AreteGateTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Judges the specs this change touched against their base: a new blocker or a lower score fails.");
            task.getPolicy().set(arete.getPolicy());
            task.getPolicySources().set(arete.getPolicySources());
            task.getMavenRepositories().set(arete.getMavenRepositories());
            task.getMavenSettings().set(arete.getMavenSettings());
            task.getUseMavenSettings().set(arete.getUseMavenSettings());
            task.getRequirePin().set(arete.getRequirePin());
            task.getCacheDir().set(arete.getCacheDir());
            task.getUserPolicies().set(arete.getUserPolicies());
            task.getRepository().set(arete.getRepository());
            task.getTarget().set(arete.getTarget());
            task.getBaseSha().set(arete.getBaseSha());
            task.getPaths().set(arete.getPaths());
            task.getRawUrl().set(arete.getRawUrl());
            task.getRawHeaders().set(arete.getRawHeaders());
            task.getReportOnly().set(arete.getReportOnly());
            task.getReportDirectory().set(arete.getReportDirectory());
        });

        project.getTasks().register("areteScore", AreteScoreTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Scores the configured spec files and fails when one is under the bar.");
            task.getPolicy().set(arete.getPolicy());
            task.getPolicySources().set(arete.getPolicySources());
            task.getMavenRepositories().set(arete.getMavenRepositories());
            task.getMavenSettings().set(arete.getMavenSettings());
            task.getUseMavenSettings().set(arete.getUseMavenSettings());
            task.getRequirePin().set(arete.getRequirePin());
            task.getCacheDir().set(arete.getCacheDir());
            task.getUserPolicies().set(arete.getUserPolicies());
            task.getRepository().set(arete.getRepository());
            task.getSpecs().from(arete.getSpecs());
            task.getFailUnder().set(arete.getFailUnder());
            task.getReportDirectory().set(arete.getReportDirectory());
        });
    }
}
