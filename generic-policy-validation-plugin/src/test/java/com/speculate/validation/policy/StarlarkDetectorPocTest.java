package com.speculate.validation.policy;

import com.speculate.validation.policy.star.StarlarkDetectorRuntime;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POC for issue #125: proves that the three most regex-heavy detectors, ported
 * to Starlark + RE2/J, produce byte-for-byte the same occurrences as the
 * trusted-Groovy runtime — while being safe by construction.
 *
 * <p>The Groovy detector output is the oracle.
 */
class StarlarkDetectorPocTest {

    private static final String ACTION_PATH_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /getAllCustomers:
                get: { responses: { '200': { description: OK } } }
              /deleteCustomer:
                delete: { responses: { '204': { description: Deleted } } }
              /customers:
                get: { responses: { '200': { description: OK } } }
            """;

    private static final String METHOD_AND_ACTION_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /customer/get:
                post: { responses: { '200': { description: OK } } }
              /customers/123/actions/activate:
                post: { responses: { '200': { description: OK } } }
              /customers:
                get: { responses: { '200': { description: OK } } }
              /orders/123:
                patch: { responses: { '200': { description: OK } } }
            """;

    private static final String SEMANTICS_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /customers/{customer_id}:
                get: { summary: Delete customer, responses: { '200': { description: OK } } }
                post: { summary: Replace customer, responses: { '200': { description: OK } } }
                put: { summary: Partially update customer, responses: { '200': { description: OK } } }
            """;

    private static final String VERSIONED_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /v1/customers:
                get:
                  parameters:
                    - { name: Api-Version, in: header, schema: { type: string } }
                  responses:
                    '200':
                      description: OK
                      content:
                        application/vnd.acme.v2+json: { schema: { type: object } }
              /orders:
                get: { responses: { '200': { description: OK } } }
            """;

    private static final String UNVERSIONED_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /customers:
                get: { responses: { '200': { description: OK } } }
            """;

    private final PolicyBundle bundle = new PolicyBundleLoader()
            .load(new ClasspathBundleResources(getClass().getClassLoader()));
    private final GroovyDetectorRuntime groovy = new GroovyDetectorRuntime();
    private final StarlarkDetectorRuntime starlark = new StarlarkDetectorRuntime();

    @Test
    void resourcePathDetectorMatchesGroovy() {
        assertSameOccurrences("resource-path", "REST001", ACTION_PATH_SPEC);
        assertSameOccurrences("resource-path", "REST003", METHOD_AND_ACTION_SPEC);
        assertSameOccurrences("resource-path", "REST004", METHOD_AND_ACTION_SPEC);
    }

    @Test
    void operationSemanticsDetectorMatchesGroovy() {
        for (String ruleId : List.of("HTTP001", "HTTP002", "HTTP003", "HTTP006", "HTTP008")) {
            assertSameOccurrences("operation-semantics", ruleId, SEMANTICS_SPEC);
        }
    }

    @Test
    void versioningDetectorMatchesGroovy() {
        for (String ruleId : List.of("VERSION001", "VERSION002", "VERSION003", "VERSION004")) {
            assertSameOccurrences("versioning", ruleId, VERSIONED_SPEC);
            assertSameOccurrences("versioning", ruleId, UNVERSIONED_SPEC);
        }
    }

    @Test
    void starlarkPortsActuallyReportSomething() {
        // Guards against a vacuous "both empty" pass.
        assertFalse(runStarlark("resource-path", "REST001", ACTION_PATH_SPEC).isEmpty());
        assertFalse(runStarlark("operation-semantics", "HTTP001", SEMANTICS_SPEC).isEmpty());
        assertFalse(runStarlark("versioning", "VERSION001", VERSIONED_SPEC).isEmpty());
        assertFalse(runStarlark("versioning", "VERSION004", UNVERSIONED_SPEC).isEmpty());
    }

    @Test
    void sandboxRejectsCapabilityEscapes() {
        // No imports, no I/O, no reflection/introspection, no unbounded work —
        // these are language-level guarantees, not an allowlist to maintain.
        for (String hostile : List.of(
                "load(\"@x//:y.bzl\", \"z\")\ndef detect(api, rule): return []",
                "def detect(api, rule): return [open(\"/etc/passwd\").read()]",
                "def detect(api, rule): return [str(type(api))]",
                "def detect(api, rule): return [1 // 0]",
                "def detect(api, rule):\n    total = 0\n    for i in range(100000000):\n        total += i\n    return []")) {
            assertThrows(RuntimeException.class,
                    () -> starlark.execute(hostile, Map.of("paths", List.of()), Map.of("parameters", Map.of())),
                    "should have been rejected: " + hostile);
        }
    }

    // --- helpers ----------------------------------------------------------

    private void assertSameOccurrences(String detectorId, String ruleId, String spec) {
        List<List<String>> expected = normalise(groovy.execute(
                bundle.detectors().get(detectorId), apiModel(spec), bundle.rules().get(ruleId)));
        List<List<String>> actual = runStarlark(detectorId, ruleId, spec);
        assertEquals(expected, actual,
                detectorId + " / " + ruleId + " diverged from the Groovy oracle");
    }

    private List<List<String>> runStarlark(String detectorId, String ruleId, String spec) {
        String source = readResource("star-poc/" + detectorId + ".star");
        Rule rule = bundle.rules().get(ruleId);
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, String> occurrence : starlark.execute(source, apiModel(spec), rule.asMap())) {
            rows.add(List.of(
                    occurrence.getOrDefault("pointer", ""),
                    occurrence.getOrDefault("path", ""),
                    occurrence.get("message")));
        }
        return rows;
    }

    private static List<List<String>> normalise(List<Occurrence> occurrences) {
        List<List<String>> rows = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            rows.add(List.of(
                    occurrence.pointer() == null ? "" : occurrence.pointer(),
                    occurrence.path() == null ? "" : occurrence.path(),
                    occurrence.message()));
        }
        return rows;
    }

    private static Map<String, Object> apiModel(String spec) {
        return OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
    }

    private static String readResource(String path) {
        try (InputStream stream = StarlarkDetectorPocTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(stream != null, path + " must be on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }
}
