package net.dublinux.arete.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Maven goals only map configuration onto the engine; these run them against a real repository. */
class GateMojoTest {
    private static final String WITH_SUMMARY = """
            openapi: 3.0.0
            info: { title: Orders API, version: 1.0.0 }
            paths:
              /orders:
                get:
                  summary: List all orders
                  responses: { '200': { description: OK } }
            """;
    private static final String WITHOUT_SUMMARY = WITH_SUMMARY.replace("      summary: List all orders\n", "");

    private Path repo;

    @BeforeEach
    void init(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(run(tmp, "git", "--version") == 0, "git is needed");
        repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve("apis/orders"));
        run(repo, "git", "init", "-q", "-b", "main", ".");
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITH_SUMMARY);
        run(repo, "git", "add", "-A");
        run(repo, "git", "-c", "user.name=t", "-c", "user.email=t@example.test", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "base");
        run(repo, "git", "checkout", "-q", "-b", "feature");
    }

    private static int run(Path dir, String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(60, TimeUnit.SECONDS) ? process.exitValue() : -1;
        } catch (IOException | InterruptedException e) {
            return -1;
        }
    }

    private GateMojo mojo() {
        GateMojo mojo = new GateMojo();
        mojo.repository = repo.toFile();
        mojo.target = "main";
        mojo.reportDirectory = repo.resolve("target/arete").toFile();
        return mojo;
    }

    @Test
    void nothingChangedPassesAndWritesTheReports() throws Exception {
        mojo().execute();

        assertTrue(Files.readString(repo.resolve("target/arete/gate.md")).startsWith("## Areté gate: PASSED"));
        assertTrue(Files.exists(repo.resolve("target/arete/gate.json")));
        assertTrue(Files.exists(repo.resolve("target/arete/gate.sarif")));
    }

    @Test
    void aRegressionFailsTheBuildWithTheReasons() throws Exception {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);

        MojoFailureException failure = assertThrows(MojoFailureException.class, () -> mojo().execute());

        assertTrue(failure.getMessage().contains("apis/orders/openapi.yaml: score fell from"), failure.getMessage());
        assertTrue(Files.readString(repo.resolve("target/arete/gate.md")).contains("FAILED"));
    }

    @Test
    void reportOnlyWritesTheSameAndNeverFailsTheBuild() throws Exception {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);
        GateMojo mojo = mojo();
        mojo.reportOnly = true;

        mojo.execute();

        assertTrue(Files.readString(repo.resolve("target/arete/gate.md")).contains("FAILED"));
    }

    @Test
    void skipDoesNothing() throws Exception {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);
        GateMojo mojo = mojo();
        mojo.skip = true;

        mojo.execute();

        assertFalse(Files.exists(repo.resolve("target/arete/gate.md")));
    }

    @Test
    void anUnreachableBaseIsAConfigurationErrorNotAFailure() {
        GateMojo mojo = mojo();
        mojo.target = "origin/main";

        MojoExecutionException error = assertThrows(MojoExecutionException.class, mojo::execute);

        assertTrue(error.getMessage().contains("origin/main is not in this clone"), error.getMessage());
    }

    @Test
    void parametersMapOntoTheEngineConfiguration() {
        GateMojo mojo = mojo();
        mojo.baseSha = "abc123";
        mojo.paths = List.of("apis/**/api.yaml");
        mojo.policy = "Zalando";
        mojo.policySources = List.of("classpath:api-policy", "maven:org.acme:p:1#sha256=" + "a".repeat(64));
        mojo.mavenRepositories = List.of("https://repo.example.test/maven");
        mojo.requirePin = true;
        mojo.rawUrl = "https://host/{path}?ref={ref}";
        mojo.rawHeaders = java.util.Map.of("PRIVATE-TOKEN", "t");

        var config = mojo.config();

        assertEquals("abc123", config.baseSha);
        assertEquals(List.of("apis/**/api.yaml"), config.globs);
        assertEquals("Zalando", config.policy);
        assertEquals(2, config.policySources.size());
        assertEquals(List.of("https://repo.example.test/maven"), config.mavenRepositories);
        assertTrue(config.requirePin);
        assertEquals("https://host/{path}?ref={ref}", config.rawUrl);
        assertEquals("t", config.rawHeaders.get("PRIVATE-TOKEN"));
        assertEquals(repo.resolve("target/arete/gate.md"), config.reportMarkdown);
    }

    @Test
    void scoreFailsUnderTheBar() throws Exception {
        ScoreMojo mojo = new ScoreMojo();
        mojo.repository = repo.toFile();
        mojo.specs = List.of(new File(repo.toFile(), "apis/orders/openapi.yaml"));
        mojo.reportDirectory = repo.resolve("target/arete").toFile();

        mojo.execute();                       // only reports without a bar
        assertTrue(Files.readString(repo.resolve("target/arete/score.md")).contains("apis/orders/openapi.yaml"));

        mojo.failUnder = "100";
        assertThrows(MojoFailureException.class, mojo::execute);
        mojo.failUnder = "0";
        mojo.execute();
        mojo.failUnder = "lots";
        assertThrows(MojoExecutionException.class, mojo::execute);
    }
}
