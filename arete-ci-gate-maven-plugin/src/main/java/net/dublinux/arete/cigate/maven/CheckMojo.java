package net.dublinux.arete.cigate.maven;

import net.dublinux.arete.cigate.AreteGateClient;
import net.dublinux.arete.cigate.AreteGateException;
import net.dublinux.arete.cigate.Combination;
import net.dublinux.arete.cigate.GateOutcome;
import net.dublinux.arete.cigate.GateReport;
import net.dublinux.arete.cigate.GateRequest;
import net.dublinux.arete.cigate.Submitters;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Submits the module's OpenAPI spec(s) to an Areté instance and fails the
 * build if any non-optional {@code validator/policy} combination fails its
 * policy. All scoring happens on the Areté server; this goal is a thin
 * client — see {@code design-notes/build-scoring-plugins.md}.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresProject = true)
public class CheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Base URL of the Areté instance. Usually set from a profile. */
    @Parameter(property = "arete.url", defaultValue = "http://localhost:6809")
    private String areteUrl;

    /** Namespace to submit under. Defaults to the module's groupId. */
    @Parameter(property = "arete.namespace", defaultValue = "${project.groupId}")
    private String namespace;

    /** Submitter label. Defaults to a CI actor variable, else "maven". */
    @Parameter(property = "arete.submitter")
    private String submitter;

    /** The OpenAPI spec file. */
    @Parameter(property = "arete.spec", defaultValue = "${project.basedir}/src/main/resources/openapi.yaml")
    private File spec;

    /** Additional spec files, for a multi-spec module. Each spec runs every combination. */
    @Parameter
    private List<File> specs;

    /** The {@code validator/policy} combinations to run. */
    @Parameter(required = true)
    private List<CombinationConfig> combinations;

    /** Also fetch a SARIF 2.1.0 log and write it to {@code arete.sarif}. */
    @Parameter(property = "arete.sarif", defaultValue = "false")
    private boolean sarif;

    /**
     * Advanced: hold a bar stricter than the policy across all combinations —
     * {@code error} | {@code blocker} | {@code score<NN}. Omit to let each
     * policy decide (the normal case).
     */
    @Parameter(property = "arete.failOn")
    private String failOn;

    /** If Areté is unreachable, warn instead of failing the build. */
    @Parameter(property = "arete.failOnUnavailable", defaultValue = "true")
    private boolean failOnUnavailable;

    @Parameter(property = "arete.gate.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.build.directory}/arete-ci-gate", readonly = true)
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("arete-ci-gate: skipped (arete.gate.skip=true)");
            return;
        }
        if (combinations == null || combinations.isEmpty()) {
            throw new MojoExecutionException("At least one <combination> is required.");
        }

        List<Combination> combos = new ArrayList<>();
        for (CombinationConfig c : combinations) {
            combos.add(c.toCombination());
        }
        String who = Submitters.resolve(submitter, "maven", System::getenv);
        AreteGateClient client = new AreteGateClient();

        boolean anyFailure = false;
        for (File specFile : resolveSpecs()) {
            byte[] bytes = read(specFile);
            GateRequest request = GateRequest.builder()
                    .areteBaseUrl(areteUrl)
                    .namespace(namespace)
                    .submitter(who)
                    .spec(bytes, contentTypeOf(specFile))
                    .specDisplayName(project.getArtifactId() + " (" + specFile.getName() + ")")
                    .combinations(combos)
                    .sarif(sarif)
                    .failOn(failOn)
                    .build();

            GateOutcome outcome;
            try {
                outcome = client.submitAndScore(request);
            } catch (AreteGateException e) {
                if (!failOnUnavailable && e.httpStatus() == null) {
                    getLog().warn("arete-ci-gate: " + e.getMessage() + " — not failing the build (failOnUnavailable=false)");
                    continue;
                }
                throw new MojoExecutionException(e.getMessage(), e);
            }

            String report = GateReport.render(request, outcome);
            report.lines().forEach(getLog()::info);
            writeReports(specFile, report, outcome);

            if (!outcome.buildPassed()) {
                anyFailure = true;
            }
        }

        if (anyFailure) {
            throw new MojoFailureException("Areté CI Gate: a non-optional combination failed its policy. "
                    + "See " + outputDirectory + "/report.txt");
        }
    }

    private List<File> resolveSpecs() throws MojoExecutionException {
        Set<File> all = new LinkedHashSet<>();
        if (specs != null) {
            all.addAll(specs);
        }
        if (spec != null && (all.isEmpty() || spec.isFile())) {
            all.add(spec);
        }
        List<File> resolved = new ArrayList<>();
        for (File f : all) {
            if (f == null) {
                continue;
            }
            if (!f.isFile()) {
                throw new MojoExecutionException("Spec file not found: " + f);
            }
            resolved.add(f);
        }
        if (resolved.isEmpty()) {
            throw new MojoExecutionException("No spec file configured. Set <spec> or <specs>.");
        }
        return resolved;
    }

    private static byte[] read(File f) throws MojoExecutionException {
        try {
            return Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read " + f, e);
        }
    }

    private static String contentTypeOf(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith(".json") ? "application/json" : "application/yaml";
    }

    private void writeReports(File specFile, String report, GateOutcome outcome) {
        try {
            Path dir = outputDirectory.toPath();
            Files.createDirectories(dir);
            String base = specFile.getName().replaceFirst("\\.[^.]+$", "");
            Files.writeString(dir.resolve("report.txt"), report, StandardCharsets.UTF_8);
            if (outcome.sarif() != null) {
                Files.writeString(dir.resolve(base + ".sarif"), outcome.sarif(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLog().warn("arete-ci-gate: could not write report to " + outputDirectory + ": " + e.getMessage());
        }
    }
}
