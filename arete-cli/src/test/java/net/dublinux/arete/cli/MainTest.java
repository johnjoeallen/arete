package net.dublinux.arete.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command line end to end: what it prints, what it writes, and above all its exit code. */
class MainTest {
    private static final String WITH_SUMMARY = """
            openapi: 3.0.0
            info: { title: Orders, version: 1.0.0 }
            paths:
              /orders:
                get:
                  summary: List all orders
                  responses: { '200': { description: OK } }
            """;
    private static final String WITHOUT_SUMMARY = WITH_SUMMARY.replace("      summary: List all orders\n", "");

    /** Scores about 77 under Enterprise Grade, whose pass mark is 90. */
    private static final String FAILING = """
            openapi: 3.0.0
            info: { title: crm service PoC, version: draft }
            servers:
              - url: http://localhost:8080/api/v1/
            paths:
              /getCustomers:
                get:
                  summary: get all customers.
                  parameters:
                    - { name: pageSize, in: query, schema: { type: string } }
                    - { name: X-Internal-Trace, in: header, schema: { type: string } }
                  responses:
                    200:
                      description: Error loading customer list
                      headers:
                        Link: { description: related }
                    500: { description: Internal server error }
              /customer/{customerId}:
                post:
                  summary: Replace customer
                  requestBody:
                    content:
                      application/json: { schema: { type: object, properties: { customer_id: { type: integer }, password: { type: string } } } }
                  responses:
                    '200': { description: OK }
                delete:
                  summary: Delete customer
                  requestBody:
                    content:
                      application/json: { schema: { type: object } }
                  responses:
                    '204': { description: Deleted }
              /orders?status=open:
                get:
                  responses:
                    '200': { description: OK }
            components:
              schemas:
                Definition1:
                  type: object
                  properties:
                    status: { type: integer, enum: [1, 2] }
                    ssn: { type: string }
            """;

    private record Result(int code, String out, String err) { }

    private static Result run(Path cwd, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), cwd);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void helpVersionAndMistakesHaveTheRightExitCodes(@TempDir Path tmp) {
        assertEquals(0, run(tmp, "help").code());
        assertTrue(run(tmp, "--help").out().contains("arete score"));
        assertEquals(0, run(tmp, "--version").code());
        assertEquals(2, run(tmp).code(), "no command is a usage error");
        Result unknown = run(tmp, "frobnicate");
        assertEquals(2, unknown.code());
        assertTrue(unknown.err().contains("unknown command"), unknown.err());
        assertEquals(2, run(tmp, "score", "x.yaml", "--colour", "red").code());
        assertEquals(2, run(tmp, "score").code());
    }

    @Test
    void scoreWritesAScoreLineAndFindingsWithLines(@TempDir Path tmp) throws IOException {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);

        Result result = run(tmp, "score", "orders.yaml");

        assertEquals(0, result.code(), result.err());
        assertTrue(result.out().startsWith("orders.yaml  Enterprise Grade  score "), result.out());
        assertTrue(result.out().contains("DOC001"), result.out());
        assertTrue(result.out().contains("orders.yaml:5"), "the finding is on the operation's line: " + result.out());
    }

    @Test
    void failUnderMakesTheExitCodeTheDecision(@TempDir Path tmp) throws IOException {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);

        assertEquals(0, run(tmp, "score", "orders.yaml", "--fail-under", "0").code());
        assertEquals(1, run(tmp, "score", "orders.yaml", "--fail-under", "100").code());
        assertEquals(2, run(tmp, "score", "orders.yaml", "--fail-under", "lots").code());
        assertEquals(0, run(tmp, "score", "orders.yaml", "--fail-under", "policy").code(), "one missing summary still clears the pass mark of 90");
        write(tmp, "crm.yaml", FAILING);
        assertEquals(1, run(tmp, "score", "crm.yaml", "--fail-under", "policy").code(), "the pass mark of the default policy is 90");
    }

    @Test
    void jsonSarifAndMarkdownGoToTheTerminalOrToAFile(@TempDir Path tmp) throws Exception {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);

        JsonNode json = new ObjectMapper().readTree(run(tmp, "score", "orders.yaml", "--format", "json").out());
        assertEquals("orders.yaml", json.get("specs").get(0).get("file").asText());

        JsonNode sarif = new ObjectMapper().readTree(run(tmp, "score", "orders.yaml", "--format=sarif").out());
        assertTrue(sarif.get("runs").get(0).get("results").get(0).get("locations").get(0).get("physicalLocation").get("region").has("startLine"));

        Result toFile = run(tmp, "score", "orders.yaml", "--format", "md", "--out", "out/summary.md");
        assertEquals("", toFile.out());
        assertTrue(Files.readString(tmp.resolve("out/summary.md")).startsWith("## Areté: orders.yaml"));

        assertEquals(2, run(tmp, "score", "orders.yaml", "--format", "pdf").code());
    }

    @Test
    void reportIsTheFullMarkdownGroupedByRule(@TempDir Path tmp) throws IOException {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);

        String report = run(tmp, "report", "orders.yaml").out();

        assertTrue(report.contains("### DOC001 — "), report);
    }

    @Test
    void aSpecThatDoesNotParseFailsTheCheck(@TempDir Path tmp) throws IOException {
        write(tmp, "broken.yaml", "openapi: 3.0.0\ninfo: [");
        Result result = run(tmp, "score", "broken.yaml");
        assertEquals(1, result.code());
        assertTrue(result.out().contains("PARSE_ERROR"), result.out());
    }

    @Test
    void aMissingFileIsAnError(@TempDir Path tmp) {
        Result result = run(tmp, "score", "nope.yaml");
        assertEquals(2, result.code());
        assertTrue(result.err().contains("no such file"), result.err());
    }

    @Test
    void diffSaysWhatChangedAndCanFailOnARegression(@TempDir Path tmp) throws IOException {
        write(tmp, "base.yaml", WITH_SUMMARY);
        write(tmp, "head.yaml", WITHOUT_SUMMARY);

        Result result = run(tmp, "diff", "base.yaml", "head.yaml", "--format", "md");

        assertEquals(0, result.code(), "diff only reports unless asked to gate");
        assertTrue(result.out().contains("- **NEW** "), result.out());
        assertTrue(result.out().contains("`DOC001`"), result.out());
        assertTrue(result.out().contains("1 new"), result.out());
        assertEquals(1, run(tmp, "diff", "base.yaml", "head.yaml", "--fail-on-regression").code());
        assertEquals(0, run(tmp, "diff", "head.yaml", "base.yaml", "--fail-on-regression").code(), "an improvement is not a regression");
        assertEquals(0, run(tmp, "diff", "base.yaml", "base.yaml", "--fail-on-regression").code());
        assertEquals(2, run(tmp, "diff", "base.yaml").code());
    }

    @Test
    void anAreteYamlNextToTheSpecIsReadAndNoOverridesIgnoresIt(@TempDir Path tmp) throws IOException {
        write(tmp, "apis/orders/openapi.yaml", WITHOUT_SUMMARY);
        write(tmp, "apis/orders/.arete.yaml", "overrides:\n  DOC001:\n    reason: Summaries are generated.\n    disable: true\n");

        assertFalse(run(tmp, "score", "apis/orders/openapi.yaml").out().contains("DOC001  Operation"), "disabled by the folder's file");
        assertTrue(run(tmp, "score", "apis/orders/openapi.yaml").out().contains("override DOC001: Summaries are generated."));
        assertTrue(run(tmp, "score", "apis/orders/openapi.yaml", "--no-overrides").out().contains("DOC001"));
    }

    @Test
    void theNearestParentFileAppliesButNotOneOutsideTheRepository(@TempDir Path tmp) throws IOException {
        write(tmp, "outside/.arete.yaml", "overrides:\n  DOC001:\n    reason: Should never be read.\n    disable: true\n");
        Files.createDirectories(tmp.resolve("outside/repo/.git"));
        write(tmp, "outside/repo/apis/openapi.yaml", WITHOUT_SUMMARY);

        Result result = run(tmp, "score", "outside/repo/apis/openapi.yaml");

        assertTrue(result.out().contains("DOC001"), "a file above the .git directory must not apply: " + result.out());
        write(tmp, "outside/repo/.arete.yaml", "overrides:\n  DOC001:\n    reason: Inside the repository.\n    disable: true\n");
        assertFalse(run(tmp, "score", "outside/repo/apis/openapi.yaml").out().contains("DOC001  "));
    }

    @Test
    void theFoldersFileCanNameThePolicyAndAnExplicitFlagWins(@TempDir Path tmp) throws IOException {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);
        write(tmp, ".arete.yaml", "policy: Zalando\n");

        assertTrue(run(tmp, "score", "orders.yaml").out().contains("  Zalando  "));
        assertTrue(run(tmp, "score", "orders.yaml", "--policy", "Zalando Extended").out().contains("  Zalando Extended  "));
    }

    @Test
    void badOverridesAreAConfigurationError(@TempDir Path tmp) throws IOException {
        write(tmp, "orders.yaml", WITHOUT_SUMMARY);
        write(tmp, ".arete.yaml", "overrides:\n  NOPE001:\n    reason: Typo.\n    disable: true\n");

        assertEquals(2, run(tmp, "score", "orders.yaml").code());
        write(tmp, ".arete.yaml", "overrides:\n  DOC001:\n    disable: true\n");   // no reason
        assertEquals(2, run(tmp, "score", "orders.yaml").code());
    }

    @Test
    void policyVerifyLoadsTheSourcesAndSaysWhatIsInThem(@TempDir Path tmp) {
        Result ok = run(tmp, "policy", "verify");
        assertEquals(0, ok.code(), ok.err());
        assertTrue(ok.out().contains("policy: Enterprise Grade"), ok.out());

        Result bad = run(tmp, "policy", "verify", "--policy-source", "file:" + tmp.resolve("nowhere"));
        assertEquals(2, bad.code());
        assertTrue(bad.err().contains("does not exist"), bad.err());

        assertEquals(2, run(tmp, "policy", "verify", "--policy-source", "https://policies.example.test/p.zip", "--require-pin").code());
    }
}
