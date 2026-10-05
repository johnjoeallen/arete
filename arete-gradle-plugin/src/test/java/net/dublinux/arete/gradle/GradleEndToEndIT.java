package net.dublinux.arete.gradle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a real Gradle with the packaged, shaded plugin jar on its build classpath, so a clash with Gradle's own libraries
 * or a missing relocation shows up here. Skipped when there is no {@code gradle} to run or no JDK it supports.
 */
class GradleEndToEndIT {
    private static final String WITH_SUMMARY = """
            openapi: 3.0.0
            info: { title: Orders API, version: 1.0.0 }
            paths:
              /orders:
                get:
                  summary: List all orders
                  responses: { '200': { description: OK } }
            """;

    private record Run(int exit, String output) { }

    private static Path pluginJar() {
        Path target = Path.of(System.getProperty("basedir", ".")).resolve("target");
        try (var jars = Files.list(target)) {
            return jars.filter(p -> p.getFileName().toString().matches("arete-gradle-plugin-.*\\.jar") && !p.getFileName().toString().startsWith("original-"))
                    .findFirst().orElseThrow(() -> new AssertionError("no packaged plugin jar in " + target));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A JDK Gradle can run on: the current one if it is old enough, else one named by ARETE_GRADLE_JAVA_HOME. */
    private static String javaHome() {
        String override = System.getenv("ARETE_GRADLE_JAVA_HOME");
        if (override != null && !override.isBlank()) return override;
        return Runtime.version().feature() <= 21 ? System.getProperty("java.home") : null;
    }

    private static boolean has(String... command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Run run(Path dir, String javaHome, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", javaHome);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(280, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("timed out: " + String.join(" ", command));
        }
        return new Run(process.exitValue(), output);
    }

    private static void git(Path dir, String javaHome, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@example.test", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
        assertEquals(0, run(dir, javaHome, command.toArray(new String[0])).exit(), String.join(" ", args));
    }

    @Test
    void theShadedPluginGatesAndScoresInARealGradle(@TempDir Path tmp) throws Exception {
        String java = javaHome();
        Assumptions.assumeTrue(java != null, "Gradle needs a JDK it supports: set ARETE_GRADLE_JAVA_HOME");
        Assumptions.assumeTrue(has("gradle", "--version") && has("git", "--version"), "gradle and git are needed");

        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve("apis/orders"));
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITH_SUMMARY);
        Files.writeString(repo.resolve("build.gradle"), "buildscript { dependencies { classpath files('" + pluginJar().toAbsolutePath().toString().replace("\\", "/") + "') } }\n"
                + "apply plugin: 'net.dublinux.arete'\n"
                + "arete {\n    target = 'main'\n    specs.from('apis/orders/openapi.yaml')\n}\n");
        Files.writeString(repo.resolve("settings.gradle"), "rootProject.name = 'it'\n");
        Files.writeString(repo.resolve(".gitignore"), "build/\n.gradle/\n");
        git(repo, java, "init", "-q", "-b", "main", ".");
        git(repo, java, "add", "-A");
        git(repo, java, "commit", "-q", "-m", "base");
        git(repo, java, "checkout", "-q", "-b", "feature");

        Run unchanged = run(repo, java, "gradle", "--offline", "--no-daemon", "-q", "areteGate");
        assertEquals(0, unchanged.exit(), unchanged.output());
        assertTrue(Files.readString(repo.resolve("build/reports/arete/gate.md")).startsWith("## Areté gate: PASSED"));

        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITH_SUMMARY.replace("      summary: List all orders\n", ""));
        Run regression = run(repo, java, "gradle", "--offline", "--no-daemon", "-q", "areteGate");
        assertEquals(1, regression.exit(), regression.output());
        assertTrue(regression.output().contains("The Areté gate failed"), regression.output());
        assertTrue(regression.output().contains("score fell from"), regression.output());
        assertTrue(Files.readString(repo.resolve("build/reports/arete/gate.sarif")).contains("startLine"));

        Run score = run(repo, java, "gradle", "--offline", "--no-daemon", "-q", "areteScore");
        assertEquals(0, score.exit(), score.output());
        assertTrue(Files.readString(repo.resolve("build/reports/arete/score.md")).contains("apis/orders/openapi.yaml"));
    }
}
