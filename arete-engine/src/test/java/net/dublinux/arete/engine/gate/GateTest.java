package net.dublinux.arete.engine.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.report.ReportWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The merge-gate over real git repositories: it judges the change, not the debt a spec already carries. */
class GateTest {
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

    /** DOC001 (a summary is required) is a blocker here, so removing one is a new blocker. */
    private static final String STRICT = "---\nid: Strict\nrules:\n  DOC001: PROHIBITED\n---\n\n# Strict\n";

    private Path repo;
    private HttpServer server;

    @BeforeEach
    void init(@TempDir Path tmp) {
        Assumptions.assumeTrue(gitAvailable(), "git is needed for the gate tests");
        repo = tmp.resolve("repo");
        git(tmp.resolve("repo"), "init", "-q", "-b", "main", ".");
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String git(Path dir, String... args) {
        try {
            Files.createDirectories(dir);
            List<String> command = new java.util.ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@example.test", "-c", "commit.gpgsign=false"));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0, "git " + String.join(" ", args) + ": " + out);
            return out.strip();
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private void write(String path, String content) {
        try {
            Path file = repo.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void commit(String message) {
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", message);
    }

    /** main holds {@code files}; the working tree then moves to a branch off it, ready for the change under test. */
    private void baseline(Map<String, String> files) {
        files.forEach(this::write);
        commit("base");
        git(repo, "checkout", "-q", "-b", "feature");
    }

    private static Engine engine(Path userPolicies) {
        return Engine.builder().cacheDir(null).userPoliciesDir(userPolicies).build();
    }

    private GateResult gate(GateRequest.Builder request) {
        return GateRunner.run(engine(repo.resolveSibling("policies")), repo, request.target("main").build());
    }

    private GateResult gate() { return gate(GateRequest.builder()); }

    private void strictPolicy() throws IOException {
        Files.createDirectories(repo.resolveSibling("policies"));
        Files.writeString(repo.resolveSibling("policies/Strict.md"), STRICT);
        write("apis/orders/.arete.yaml", "policy: Strict\n");
    }

    @Test
    void anUnchangedSpecIsStillScoredAndPassesWhenItIsClean() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));

        GateResult result = gate();

        assertTrue(result.passed());
        assertEquals(1, result.specs().size());
        assertTrue(result.specs().get(0).unchanged());
        assertFalse(result.specs().get(0).isNew());
        assertTrue(result.specs().get(0).head().score() > 0);
    }

    @Test
    void anUnchangedSpecWithABlockerFailsTheGate() throws IOException {
        strictPolicy();
        baseline(Map.of("apis/orders/openapi.yaml", WITHOUT_SUMMARY, "apis/orders/.arete.yaml", "policy: Strict\n"));

        GateResult result = gate();

        assertFalse(result.passed());
        SpecResult spec = result.specs().get(0);
        assertTrue(spec.unchanged());
        assertTrue(spec.reasons().stream().anyMatch(r -> r.startsWith("unchanged spec has 1 blocker")), spec.reasons().toString());
    }

    @Test
    void aNewBlockerFailsTheGateAndSaysWhere() throws IOException {
        strictPolicy();
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY, "apis/orders/.arete.yaml", "policy: Strict\n"));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);

        GateResult result = gate();

        assertFalse(result.passed());
        SpecResult spec = result.specs().get(0);
        assertEquals("apis/orders/openapi.yaml", spec.file());
        assertTrue(spec.reasons().get(0).startsWith("new blocker: DOC001 at apis/orders/openapi.yaml:5"), spec.reasons().toString());
        assertTrue(spec.reasons().stream().anyMatch(r -> r.startsWith("score fell from 100 to 0")), spec.reasons().toString());
    }

    @Test
    void aWarningThatLowersTheScoreFailsWithTheRuleThatCausedIt() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);

        GateResult result = gate();

        assertFalse(result.passed());
        String reason = result.specs().get(0).reasons().get(0);
        assertTrue(reason.startsWith("score fell from "), reason);
        assertTrue(reason.contains("newly violated: DOC001 (-0.5)"), reason);
    }

    @Test
    void debtThatAlreadyExistedIsNotArguedAgain() {
        baseline(Map.of("apis/orders/openapi.yaml", WITHOUT_SUMMARY));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY.replace("version: 1.0.0", "version: 1.1.0"));

        GateResult result = gate();

        assertTrue(result.passed(), result.reasons().toString());
        assertEquals(1, result.specs().size());
        assertTrue(result.specs().get(0).diff().count(net.dublinux.arete.engine.report.ScoreDiff.Kind.EXISTING) > 0, "it still has its findings");
        assertEquals(0, result.specs().get(0).diff().count(net.dublinux.arete.engine.report.ScoreDiff.Kind.NEW));
    }

    @Test
    void fixingThingsPasses() {
        baseline(Map.of("apis/orders/openapi.yaml", WITHOUT_SUMMARY));
        write("apis/orders/openapi.yaml", WITH_SUMMARY);

        GateResult result = gate();

        assertTrue(result.passed(), result.reasons().toString());
        assertEquals(1, result.specs().get(0).diff().count(net.dublinux.arete.engine.report.ScoreDiff.Kind.RESOLVED));
    }

    @Test
    void insertingAParameterDoesNotMakeTheOthersLookNew() {
        String base = WITH_SUMMARY.replace("      responses", "      parameters:\n        - { name: limit, in: query, schema: { type: integer } }\n        - { name: cursor, in: query, schema: { type: string } }\n      responses");
        String head = base.replace("        - { name: limit", "        - { name: offset, in: query, description: Where to start, example: 0, schema: { type: integer } }\n        - { name: limit");
        baseline(Map.of("apis/orders/openapi.yaml", base));
        write("apis/orders/openapi.yaml", head);

        GateResult result = gate();

        assertTrue(result.passed(), result.reasons().toString());
        assertEquals(0, result.specs().get(0).diff().count(net.dublinux.arete.engine.report.ScoreDiff.Kind.NEW), result.specs().get(0).diff().changes().toString());
        assertEquals(0, result.specs().get(0).diff().count(net.dublinux.arete.engine.report.ScoreDiff.Kind.RESOLVED));
    }

    @Test
    void aNewSpecMustStandOnItsOwn() throws IOException {
        strictPolicy();
        baseline(Map.of("README.md", "hi"));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);       // a blocker under Strict
        write("apis/payments/openapi.yaml", WITH_SUMMARY);       // fine under the default policy

        GateResult result = gate();

        assertEquals(2, result.specs().size());
        SpecResult orders = result.specs().get(0);
        assertTrue(orders.isNew());
        assertFalse(orders.passed());
        assertTrue(orders.reasons().get(0).contains("new spec has 1 blocker"), orders.reasons().toString());
        SpecResult payments = result.specs().get(1);
        assertTrue(payments.isNew());
        assertEquals(payments.head().meetsPassingScore(), payments.passed());
    }

    @Test
    void aMovedSpecIsComparedWithItsOldSelfNotJudgedAsNew() {
        baseline(Map.of("apis/old/openapi.yaml", WITHOUT_SUMMARY));
        try {
            Files.createDirectories(repo.resolve("apis/new-name"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        git(repo, "mv", "apis/old/openapi.yaml", "apis/new-name/openapi.yaml");

        GateResult result = gate();

        assertTrue(result.passed(), result.reasons().toString());
        assertEquals(List.of(), result.specs().stream().filter(SpecResult::isNew).toList());
    }

    @Test
    void aDeletedSpecAndFilesThatAreNotSpecsAreIgnored() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY, "docs/notes.yaml", "a: 1\n"));
        git(repo, "rm", "-q", "apis/orders/openapi.yaml");
        write("docs/notes.yaml", "a: 2\n");

        GateResult result = gate();

        assertTrue(result.passed());
        assertEquals(0, result.specs().size());
    }

    @Test
    void globsChooseTheSpecs() {
        baseline(Map.of("apis/orders/api.yaml", WITH_SUMMARY));
        write("apis/orders/api.yaml", WITHOUT_SUMMARY);

        assertEquals(0, gate().specs().size(), "api.yaml is not named openapi.*");
        assertEquals(1, gate(GateRequest.builder().glob("apis/**/api.yaml")).specs().size());
        assertEquals(1, gate(GateRequest.builder().glob("**/api.yaml")).specs().size());
    }

    @Test
    void aHeadThatDoesNotParseFailsAndABaseThatDoesNotIsTreatedAsNew() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY, "apis/payments/openapi.yaml", "openapi: 3.0.0\ninfo: [\n"));
        write("apis/orders/openapi.yaml", "openapi: 3.0.0\ninfo: [\n");
        write("apis/payments/openapi.yaml", WITH_SUMMARY);

        GateResult result = gate();

        SpecResult orders = result.specs().get(0);
        assertFalse(orders.passed());
        assertTrue(orders.reasons().get(0).startsWith("does not parse"), orders.reasons().toString());
        SpecResult payments = result.specs().get(1);
        assertTrue(payments.isNew());
        assertTrue(payments.warning().contains("base did not parse"), payments.warning());
    }

    @Test
    void anOverridesFileAppliesToBaseAndHeadAlike() throws IOException {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));
        write("apis/orders/.arete.yaml", "overrides:\n  DOC001:\n    reason: Summaries are generated.\n    disable: true\n");
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);

        GateResult result = gate();

        assertTrue(result.passed(), "with DOC001 disabled, losing the summary costs nothing: " + result.reasons());
    }

    @Test
    void anUnknownTargetOrBaseIsAConfigurationErrorThatNamesTheFix() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);

        GateException missingTarget = assertThrows(GateException.class,
                () -> GateRunner.run(engine(repo.resolveSibling("p")), repo, GateRequest.builder().target("origin/main").build()));
        assertTrue(missingTarget.getMessage().contains("origin/main is not in this clone"), missingTarget.getMessage());

        GateException missingBase = assertThrows(GateException.class,
                () -> GateRunner.run(engine(repo.resolveSibling("p")), repo, GateRequest.builder().baseSha("0".repeat(40)).build()));
        assertTrue(missingBase.getMessage().contains("is not in this clone"), missingBase.getMessage());
    }

    @Test
    void anExplicitBaseShaIsUsedInPlaceOfTheMergeBase() {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));
        String sha = git(repo, "rev-parse", "main");
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);

        GateResult result = GateRunner.run(engine(repo.resolveSibling("p")), repo, GateRequest.builder().baseSha(sha).build());

        assertFalse(result.passed());
    }

    @Test
    void rawModeFetchesTheBaseFromTheCodeHostForAShallowClone() throws IOException {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY, "apis/stable/openapi.yaml", WITH_SUMMARY));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);       // changed: worse
        write("apis/payments/openapi.yaml", WITH_SUMMARY);       // new: the host has no base for it
        List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawQuery() + "|" + exchange.getRequestURI().getRawPath();
            asked.add(path);
            String file = java.net.URLDecoder.decode(exchange.getRequestURI().getRawPath().substring("/files/".length()), StandardCharsets.UTF_8);
            String body = file.equals("apis/orders/openapi.yaml") || file.equals("apis/stable/openapi.yaml") ? WITH_SUMMARY : null;
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        String template = "http://127.0.0.1:" + server.getAddress().getPort() + "/files/{path}?ref={ref}";

        GateResult result = GateRunner.run(engine(repo.resolveSibling("p")), repo,
                GateRequest.builder().baseSha("abc123def456").raw(template, Map.of()).build());

        assertEquals(3, result.specs().size(), "every spec is scored, including the one identical to its base");
        assertEquals(List.of("apis/stable/openapi.yaml"), result.unchanged().stream().map(SpecResult::file).toList());
        assertFalse(result.specs().get(0).isNew());
        assertFalse(result.specs().get(0).passed());
        assertTrue(result.specs().get(1).isNew());
        assertTrue(asked.stream().anyMatch(a -> a.startsWith("ref=abc123def456|/files/apis%2Forders%2Fopenapi.yaml")), asked.toString());
        assertThrows(GateException.class, () -> GateRequest.builder().raw(template, Map.of()).build());
    }

    @Test
    void theWritersCarryTheVerdictTheReasonsAndOnlyWhatIsNew() throws Exception {
        baseline(Map.of("apis/orders/openapi.yaml", WITH_SUMMARY));
        write("apis/orders/openapi.yaml", WITHOUT_SUMMARY);
        GateResult result = gate();

        String markdown = ReportWriter.markdown(result);
        assertTrue(markdown.startsWith("## Areté gate: FAILED"), markdown);
        assertTrue(markdown.contains("**Why it failed**"), markdown);
        assertTrue(markdown.contains("### apis/orders/openapi.yaml — failed"), markdown);
        assertTrue(markdown.contains("- **NEW** "), markdown);

        JsonNode json = new ObjectMapper().readTree(ReportWriter.json(result));
        assertEquals("gate", json.get("kind").asText());
        assertFalse(json.get("passed").asBoolean());
        assertEquals(1, json.get("specs").get(0).get("counts").get("new").asInt());

        JsonNode sarif = new ObjectMapper().readTree(ReportWriter.sarif(result));
        assertEquals(1, sarif.get("runs").get(0).get("results").size());
        assertEquals("apis/orders/openapi.yaml", sarif.get("runs").get(0).get("results").get(0).get("locations").get(0)
                .get("physicalLocation").get("artifactLocation").get("uri").asText());

        assertTrue(ReportWriter.text(result).startsWith("gate: FAILED"));
    }
}
