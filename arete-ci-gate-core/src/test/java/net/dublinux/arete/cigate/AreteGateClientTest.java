package net.dublinux.arete.cigate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreteGateClientTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> requestPaths = new ArrayList<>();
    private final AtomicReference<Handler> handler = new AtomicReference<>();

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPaths.add(exchange.getRequestURI().toString());
            handler.get().handle(exchange);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private GateRequest.Builder request() {
        return GateRequest.builder()
                .areteBaseUrl(baseUrl)
                .namespace("payments")
                .submitter("maven")
                .spec("openapi: 3.0.0\ninfo: {title: T, version: 1}\npaths: {}\n")
                .specDisplayName("my-service");
    }

    @Test
    void aPassingResponseYieldsABuildPass() throws Exception {
        handler.set(ex -> respond(ex, 201, """
                { "spec": { "id": "abc-123" }, "ok": true, "verdict": "PASS",
                  "results": [
                    { "validator": "generic-policy", "policy": "Enterprise Grade",
                      "status": "SUCCESS", "score": 93.5, "grade": "B+", "passingScore": 90.0,
                      "level": { "criterion": "score<90", "source": "policy", "met": true },
                      "counts": { "error": 0, "warning": 20 } } ] }
                """));

        GateOutcome outcome = new AreteGateClient().submitAndScore(
                request().combination(Combination.gating("generic-policy", "Enterprise Grade")).build());

        assertTrue(outcome.buildPassed());
        assertEquals("abc-123", outcome.specId());
        assertEquals(1, outcome.combinations().size());
        assertEquals(93.5, outcome.combinations().get(0).score());
    }

    @Test
    void a422WithResultsIsAScoringFailureNotAnError() throws Exception {
        handler.set(ex -> respond(ex, 422, """
                { "spec": { "id": "abc-123" }, "ok": false, "verdict": "FAIL",
                  "results": [
                    { "validator": "generic-policy", "policy": "Enterprise Grade",
                      "status": "SUCCESS", "score": 71.0, "grade": "D", "passingScore": 90.0,
                      "level": { "criterion": "score<90", "source": "policy", "met": false },
                      "counts": { "error": 2 } } ] }
                """));

        GateOutcome outcome = new AreteGateClient().submitAndScore(
                request().combination(Combination.gating("generic-policy", "Enterprise Grade")).build());

        assertFalse(outcome.buildPassed());
        assertEquals(1, outcome.gatingFailures().size());
    }

    @Test
    void a422FromAnOptionalCombinationOnlyIsStillABuildPass() throws Exception {
        handler.set(ex -> respond(ex, 422, """
                { "spec": { "id": "abc-123" }, "verdict": "FAIL",
                  "results": [
                    { "validator": "generic-policy", "policy": "Enterprise Grade",
                      "status": "SUCCESS", "score": 95.0,
                      "level": { "criterion": "score<90", "source": "policy", "met": true } },
                    { "validator": "generic-policy", "policy": "Zalando",
                      "status": "SUCCESS", "score": 60.0,
                      "level": { "criterion": "error", "source": "policy", "met": false } } ] }
                """));

        GateOutcome outcome = new AreteGateClient().submitAndScore(request()
                .combination(Combination.gating("generic-policy", "Enterprise Grade"))
                .combination(new Combination("generic-policy", "Zalando", true))
                .build());

        assertTrue(outcome.buildPassed());
    }

    @Test
    void a422ProblemDetailIsABuildError() throws Exception {
        handler.set(ex -> respond(ex, 422, """
                { "status": 422, "title": "Unprocessable Entity", "detail": "unknown validator 'nope'" }
                """));

        AreteGateException e = assertThrows(AreteGateException.class, () -> new AreteGateClient().submitAndScore(
                request().combination(Combination.gating("nope", "x")).build()));
        assertTrue(e.getMessage().contains("unknown validator"));
    }

    @Test
    void aConnectionFailureIsABuildError() {
        AreteGateException e = assertThrows(AreteGateException.class, () -> new AreteGateClient().submitAndScore(
                GateRequest.builder().areteBaseUrl("http://127.0.0.1:1")
                        .namespace("n").submitter("maven")
                        .spec("openapi: 3.0.0")
                        .combination(Combination.gating("v", "p")).build()));
        assertTrue(e.getMessage().contains("not reachable"));
    }

    @Test
    void sarifIsFetchedAsAFollowUpWhenRequested() throws Exception {
        handler.set(ex -> {
            if (ex.getRequestURI().toString().contains("format=sarif")) {
                respond(ex, 200, "{ \"version\": \"2.1.0\", \"runs\": [] }");
            } else {
                respond(ex, 201, """
                        { "spec": { "id": "abc-123" }, "verdict": "PASS",
                          "results": [ { "validator": "generic-policy", "policy": "Enterprise Grade",
                            "status": "SUCCESS", "level": { "met": true } } ] }
                        """);
            }
        });

        GateOutcome outcome = new AreteGateClient().submitAndScore(
                request().sarif(true)
                        .combination(Combination.gating("generic-policy", "Enterprise Grade")).build());

        assertNotNull(outcome.sarif());
        assertTrue(outcome.sarif().contains("2.1.0"));
        assertTrue(requestPaths.stream().anyMatch(p -> p.contains("format=sarif")));
    }

    @Test
    void theSubmitCallAlwaysSendsHttpStatusOnFail422() throws Exception {
        handler.set(ex -> respond(ex, 201, """
                { "spec": { "id": "x" }, "verdict": "PASS",
                  "results": [ { "validator": "v", "policy": "p", "status": "SUCCESS",
                    "level": { "met": true } } ] }
                """));

        new AreteGateClient().submitAndScore(request().combination(Combination.gating("v", "p")).build());

        assertTrue(requestPaths.get(0).contains("httpStatusOnFail=422"));
        assertTrue(requestPaths.get(0).contains("run=v%2Fp"));
    }
}
