package net.dublinux.arete.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code arete gate} in a real repository: the exit code decides, and the files are there for a later CI step. */
class GateCommandTest {
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
    private HttpServer server;

    @BeforeEach
    void init(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(run(tmp, "git", "--version") == 0, "git is needed");
        repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve("apis/orders"));
        run(repo, "git", "init", "-q", "-b", "main", ".");
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITH_SUMMARY);
        commit("base");
        run(repo, "git", "checkout", "-q", "-b", "feature");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private void commit(String message) {
        run(repo, "git", "add", "-A");
        run(repo, "git", "-c", "user.name=t", "-c", "user.email=t@example.test", "-c", "commit.gpgsign=false", "commit", "-q", "-m", message);
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

    private record Result(int code, String out, String err) { }

    private Result gate(String... args) {
        List<String> argv = new ArrayList<>(List.of("gate", "--policy-source", "classpath:api-policy"));
        argv.addAll(List.of(args));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(argv.toArray(new String[0]), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), repo);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void nothingChangedPasses() {
        Result result = gate("--target", "main");
        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().startsWith("gate: PASSED"), result.out());
    }

    @Test
    void aRegressionFailsWritesTheReportFilesAndNamesWhy() throws IOException {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);

        Result result = gate("--target", "main", "--report-json", "out/gate.json", "--report-md", "out/gate.md", "--report-sarif", "out/gate.sarif");

        assertEquals(1, result.code(), result.err());
        assertTrue(result.out().contains("FAIL  apis/orders/openapi.yaml"), result.out());
        assertTrue(result.out().contains("score fell from"), result.out());
        assertTrue(Files.readString(repo.resolve("out/gate.md")).startsWith("## Areté gate: FAILED"));
        assertTrue(Files.readString(repo.resolve("out/gate.json")).contains("\"kind\" : \"gate\""));
        assertTrue(Files.readString(repo.resolve("out/gate.sarif")).contains("\"startLine\""));
    }

    @Test
    void reportOnlyWritesTheSameAndNeverBlocks() throws IOException {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);

        Result result = gate("--target", "main", "--report-only", "--report-md", "gate.md");

        assertEquals(0, result.code());
        assertTrue(Files.readString(repo.resolve("gate.md")).contains("FAILED"));
    }

    @Test
    void anUnreachableBaseIsAConfigurationErrorNotAVerdict() throws IOException {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);

        Result result = gate("--target", "origin/main");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("origin/main is not in this clone"), result.err());
    }

    @Test
    void theFormatAndOutFlagsChooseWhatIsPrinted() throws IOException {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);

        assertTrue(gate("--target", "main", "--format", "md").out().startsWith("## Areté gate: FAILED"));
        assertEquals(2, gate("--target", "main", "--format", "pdf").code());
        assertEquals(2, gate("--target", "main", "stray").code());
        assertEquals(2, gate("--target", "main", "--base-source", "carrier-pigeon").code());
    }

    @Test
    void rawModeUsesTheCodeHostAndTheListedFiles() throws IOException {
        Files.writeString(repo.resolve("apis/orders/openapi.yaml"), WITHOUT_SUMMARY);
        Files.writeString(repo.resolve("changed.txt"), "apis/orders/openapi.yaml\n");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = WITH_SUMMARY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/files/{path}/raw?ref={ref}";

        Result failing = gate("--base-source", "raw", "--raw-url", url, "--base-sha", "abc123", "--changed-files", "changed.txt");
        assertEquals(1, failing.code(), failing.err());

        Result missingVariable = gate("--base-source", "raw", "--raw-url", url, "--base-sha", "abc123", "--raw-header", "PRIVATE-TOKEN: $ARETE_TEST_SURELY_NOT_SET");
        assertEquals(2, missingVariable.code());
        assertTrue(missingVariable.err().contains("ARETE_TEST_SURELY_NOT_SET"), missingVariable.err());

        assertEquals(2, gate("--base-source", "raw", "--raw-url", url).code(), "raw mode needs --base-sha");
        assertEquals(2, gate("--base-source", "raw").code(), "raw mode needs --raw-url");
    }
}
