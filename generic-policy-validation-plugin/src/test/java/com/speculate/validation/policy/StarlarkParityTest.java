package com.speculate.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Issue #125 — every bundled detector, ported to Starlark, must produce exactly
 * the same occurrences as the trusted-Groovy runtime.
 *
 * <p>Rather than hand-craft a trigger spec per rule, this drives the whole
 * bundle the way {@link GenericPolicyValidationPlugin#validate} does — every
 * policy, every disposition, effective (rule + policy-override) parameters —
 * against a corpus of deliberately messy specs, and asserts the Groovy and
 * Starlark occurrence lists are identical for each (spec, policy, rule).
 */
class StarlarkParityTest {

    private final PolicyBundle bundle = new PolicyBundleLoader()
            .load(new ClasspathBundleResources(getClass().getClassLoader()));
    private final PolicyBundle groovyBundle = new PolicyBundleLoader()
            .load(new ClasspathBundleResources(getClass().getClassLoader()),
                    new PolicyBundleLoader.LoadOptions(true));
    private final GroovyDetectorRuntime groovy = new GroovyDetectorRuntime();
    private final StarlarkDetectorRuntime starlark = new StarlarkDetectorRuntime();

    @Test
    void everyPortedDetectorMatchesGroovyAcrossTheCorpus() {
        List<String> mismatches = new ArrayList<>();
        int comparisons = 0;
        int nonEmpty = 0;
        Map<String, Integer> nonEmptyByDetector = new TreeMap<>();

        for (Map.Entry<String, String> specEntry : CORPUS.entrySet()) {
            Map<String, Object> api = apiModel(specEntry.getValue());

            // (a) every policy disposition — mirrors GenericPolicyValidationPlugin.validate,
            //     covering effective (rule + policy-override) parameters.
            for (Map.Entry<String, Policy> policyEntry : bundle.policies().entrySet()) {
                for (Map.Entry<String, PolicyDisposition> disposition : policyEntry.getValue().dispositions().entrySet()) {
                    Rule rule = bundle.rules().get(disposition.getKey());
                    Map<String, Object> parameters = new LinkedHashMap<>(rule.parameters());
                    parameters.putAll(disposition.getValue().parameters());
                    Result r = compare(api, rule, parameters);
                    if (r == null) {
                        continue;
                    }
                    comparisons++;
                    if (r.nonEmpty) {
                        nonEmpty++;
                        nonEmptyByDetector.merge(rule.detector(), 1, Integer::sum);
                    }
                    if (r.mismatch != null) {
                        mismatches.add(specEntry.getKey() + " / " + policyEntry.getKey() + " / " + r.mismatch);
                    }
                }
            }

            // (b) every rule with its own declared parameters — reaches rules no
            //     bundled policy references yet (CONTENT*, extra STANDARD*/BULK*).
            for (Rule rule : bundle.rules().values()) {
                Result r = compare(api, rule, rule.parameters());
                if (r == null) {
                    continue;
                }
                comparisons++;
                if (r.nonEmpty) {
                    nonEmpty++;
                    nonEmptyByDetector.merge(rule.detector(), 1, Integer::sum);
                }
                if (r.mismatch != null) {
                    mismatches.add(specEntry.getKey() + " / (rule) / " + r.mismatch);
                }
            }
        }

        // (c) a few explicit parameter overrides for checks the corpus versions
        //     cannot express (the parser only accepts OpenAPI 3.0/3.1).
        for (Map.Entry<String, String> specEntry : CORPUS.entrySet()) {
            Map<String, Object> api = apiModel(specEntry.getValue());
            Rule openApiVersion = bundle.rules().get("STANDARD010");
            Result r = compare(api, openApiVersion, Map.of("allowed", "3.1"));
            if (r != null) {
                comparisons++;
                if (r.nonEmpty) {
                    nonEmpty++;
                    nonEmptyByDetector.merge("openapi-version", 1, Integer::sum);
                }
                if (r.mismatch != null) {
                    mismatches.add(specEntry.getKey() + " / (override allowed=3.1) / " + r.mismatch);
                }
            }
        }

        assertTrue(comparisons > 500, "expected a broad sweep, ran only " + comparisons);
        assertTrue(nonEmpty > 40, "corpus barely triggered any rule (" + nonEmpty + " non-empty); parity would be vacuous");

        // Every detector that can report something must have been exercised
        // non-vacuously, or its parity here is meaningless.
        List<String> alwaysEmpty = List.of("manual", "compatibility");
        List<String> untested = new ArrayList<>();
        for (String detectorId : bundle.detectors().keySet()) {
            if (!alwaysEmpty.contains(detectorId) && !nonEmptyByDetector.containsKey(detectorId)) {
                untested.add(detectorId);
            }
        }
        assertTrue(untested.isEmpty(),
                "corpus never triggered a non-empty result for: " + untested
                        + "\nnon-empty counts: " + nonEmptyByDetector);

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " detector parity mismatch(es):\n\n" + String.join("\n\n", mismatches));
        }
    }

    @Test
    void theDefaultBundleRunsEveryDetectorOnStarlark() {
        for (Map.Entry<String, Detector> detector : bundle.detectors().entrySet()) {
            assertEquals("starlark", detector.getValue().language(),
                    detector.getKey() + " should have been loaded as a Starlark detector by default");
        }
    }

    @Test
    void theGroovyForcedBundleKeepsEveryDetectorOnGroovy() {
        for (Map.Entry<String, Detector> detector : groovyBundle.detectors().entrySet()) {
            assertEquals("groovy", detector.getValue().language(),
                    detector.getKey() + " should stay on Groovy when forced");
        }
    }

    // --- helpers ----------------------------------------------------------

    private static final class Result {
        final boolean nonEmpty;
        final String mismatch; // null when Groovy and Starlark agree

        Result(boolean nonEmpty, String mismatch) {
            this.nonEmpty = nonEmpty;
            this.mismatch = mismatch;
        }
    }

    /** Runs one detector both ways for one rule+params; null when the detector is not bundled. */
    private Result compare(Map<String, Object> api, Rule rule, Map<String, Object> parameters) {
        Detector groovyDetector = groovyBundle.detectors().get(rule.detector());
        Detector starlarkDetector = bundle.detectors().get(rule.detector());
        if (groovyDetector == null || starlarkDetector == null) {
            return null;
        }
        Rule effective = new Rule(rule.id(), rule.title(), rule.category(), rule.detector(),
                rule.scope(), parameters, rule.documentationMarkdown());

        List<List<String>> expected = normalise(groovy.execute(groovyDetector, api, effective));
        try {
            List<List<String>> actual = normalise(starlark.execute(starlarkDetector, api, effective));
            String mismatch = expected.equals(actual)
                    ? null
                    : rule.id() + " [" + rule.detector() + "]\n    groovy=" + expected + "\n    star  =" + actual;
            return new Result(!expected.isEmpty(), mismatch);
        } catch (RuntimeException e) {
            return new Result(!expected.isEmpty(), rule.id() + " [" + rule.detector() + "] — Starlark threw: " + e);
        }
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

    // --- corpus: deliberately non-conforming specs -----------------------

    private static final Map<String, String> CORPUS = new TreeMap<>(Map.ofEntries(
            Map.entry("action-paths", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /getAllCustomers:
                        get: { responses: { '200': { description: OK } } }
                      /deleteCustomer:
                        delete: { responses: { '204': { description: Deleted } } }
                      /customers:
                        get: { responses: { '200': { description: OK } } }
                    """),
            Map.entry("rpc-and-actions", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customer/get:
                        post: { responses: { '200': { description: OK } } }
                      /customers/123/actions/activate:
                        post: { responses: { '200': { description: OK } } }
                      /customers:
                        get:
                          requestBody: { required: true, content: { application/json: { schema: { type: object } } } }
                          responses: { '200': { description: OK } }
                        delete:
                          requestBody: { required: true, content: { application/json: { schema: { type: object } } } }
                          responses: { '204': { description: Deleted } }
                      /orders/123:
                        patch: { responses: { '200': { description: OK } } }
                    """),
            Map.entry("compliant-starter", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customers:
                        get: { summary: Get customers, responses: { '200': { description: OK } } }
                      /orders:
                        get: { summary: Get orders, responses: { '200': { description: OK } } }
                    """),
            Map.entry("summary-style", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customers:
                        get: { summary: 'get customers.', responses: { '200': { description: OK } } }
                      /orders:
                        get:
                          summary: This is a deliberately very long operation summary that is designed to exceed the configured maximum length for concise API documentation.
                          responses: { '200': { description: OK } }
                    """),
            Map.entry("naming-and-schema", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customerOrders/{customerId}:
                        parameters:
                          - { name: customerId, in: path, required: true, schema: { type: string } }
                        get:
                          parameters:
                            - { name: customerStatus, in: query, schema: { type: string } }
                            - { name: XCustomHeader, in: header, schema: { type: string } }
                          responses: { '200': { description: OK } }
                    components:
                      schemas:
                        CreateCustomerRequest:
                          type: object
                          properties:
                            customer_name: { type: string }
                            customer: { type: array, items: { type: string } }
                            status: { type: string, enum: [ACTIVE, DISABLED] }
                            rank: { type: integer, enum: [1, 2] }
                            legacy: { type: string, nullable: true }
                            created: { type: string }
                            amount: { type: number }
                            tags:
                              type: array
                              items: { type: string }
                              maxItems: 10
                        OrderResponse:
                          type: object
                          properties:
                            id: { type: integer }
                            createdAt: { type: string, format: date-time }
                            state: { type: string, enum: [open, Closed] }
                    """),
            Map.entry("semantics", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customers/{customer_id}:
                        get: { summary: Delete customer, responses: { '200': { description: OK } } }
                        post: { summary: Replace customer, responses: { '200': { description: OK } } }
                        put: { summary: Partially update customer, responses: { '200': { description: OK } } }
                    """),
            Map.entry("headers-and-media", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /customers:
                        get:
                          parameters:
                            - { name: X-Internal-Trace, in: header, schema: { type: string } }
                            - { name: X-Request-Id, in: header, schema: { type: string } }
                            - name: tags
                              in: query
                              style: pipeDelimited
                              explode: false
                              schema: { type: array, items: { type: string } }
                          responses:
                            '200':
                              description: OK
                              headers:
                                X-Internal-Region: { schema: { type: string } }
                                Content-Type: { schema: { type: string } }
                              content:
                                application/*: { schema: { type: object } }
                            '429':
                              description: Too many requests
                              headers:
                                RateLimit-Limit: { schema: { type: integer } }
                        post:
                          requestBody:
                            content:
                              text/plain: { schema: { type: string } }
                          responses:
                            '500':
                              description: 'Server error: invalid state'
                              content:
                                application/xml: { schema: { type: string } }
                    """),
            Map.entry("security-and-version", """
                    openapi: 3.0.3
                    info:
                      title: Test API
                      version: 2.1.0
                      contact: { name: Team, email: team@example.com }
                      description: An API.
                    servers:
                      - url: https://API_GATEWAY.internal.example.com/v9
                    security:
                      - bearerAuth: [read]
                    paths:
                      /v1/customers:
                        get: { responses: { '200': { description: OK } } }
                      /public:
                        get: { security: [], responses: { '200': { description: OK } } }
                      /admin:
                        get:
                          security:
                            - apiKey: []
                          responses: { '200': { description: OK } }
                    """),
            Map.entry("bulk-ops", """
                    openapi: 3.0.0
                    info: { title: Test API, version: 1.0.0 }
                    paths:
                      /createCustomers:
                        get: { summary: Bulk create customers, responses: { '200': { description: OK } } }
                      /customers/{id}/bulk:
                        post: { summary: Create many, responses: { '200': { description: OK } } }
                      /customers/query:
                        put: { summary: Bulk update by search filter, responses: { '200': { description: OK } } }
                      /orders:
                        post: { summary: Create order, responses: { '201': { description: Created } } }
                    """),
            Map.entry("empty-paths", """
                    openapi: 3.1.0
                    info: { title: Test API, version: 1.0.0 }
                    paths: {}
                    """)));
}
