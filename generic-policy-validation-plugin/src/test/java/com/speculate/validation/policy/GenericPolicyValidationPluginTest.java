package com.speculate.validation.policy;

import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.ValidationResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericPolicyValidationPluginTest {
    private static final String ACTION_PATH_SPEC = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /getAllCustomers:
                get:
                  responses:
                    '200': { description: OK }
              /deleteCustomer:
                delete:
                  responses:
                    '204': { description: Deleted }
              /customers:
                get:
                  responses:
                    '200': { description: OK }
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
                get:
                  requestBody: { required: true, content: { application/json: { schema: { type: object } } } }
                  responses: { '200': { description: OK } }
                delete:
                  requestBody: { required: true, content: { application/json: { schema: { type: object } } } }
                  responses: { '204': { description: Deleted } }
              /orders/123:
                patch: { responses: { '200': { description: OK } } }
            """;

    private static final String COMPLIANT_STARTER_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /customers:
                get:
                  summary: Get customers
                  responses: { '200': { description: OK } }
              /orders:
                get:
                  summary: Get orders
                  responses: { '200': { description: OK } }
            """;

    private static final String SUMMARY_STYLE_SPEC = """
            openapi: 3.0.0
            info: { title: Test API, version: 1.0.0 }
            paths:
              /customers:
                get:
                  summary: get customers.
                  responses: { '200': { description: OK } }
              /orders:
                get:
                  summary: This is a deliberately very long operation summary that is designed to exceed the configured maximum length for concise API documentation.
                  responses: { '200': { description: OK } }
            """;

    private static final String NAMING_SPEC = """
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
            """;

    @Test
    void executesTheStrictGroovyDetectorAndDeductsOnlyOncePerRule() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(50, result.getRulesEvaluatedCount());
        assertEquals(9, result.getViolations().size());
        assertEquals("REST001", result.getViolations().get(0).getRuleId());
        assertEquals(2, result.getViolations().stream().filter(violation -> violation.getRuleId().equals("REST001")).count());
        assertEquals(97.5, result.getOverallScore());
        assertEquals(97.5, result.getOverallScoreWithoutBlockers());
        assertEquals(0.5, result.getViolations().get(0).getScoreImprovement());
        assertEquals(0.5, result.getViolations().get(1).getScoreImprovement());
        assertEquals("http://localhost:6809/plugins/generic-policy/rules/REST001",
                result.getViolations().get(0).getDocumentationUrl());
        assertTrue(plugin.getRuleDocumentation("REST001").orElseThrow().markdown().contains("GET /customers"));
    }

    @Test
    void returnsAFullScoreWhenNoStrictRuleMatches() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(COMPLIANT_STARTER_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(50, result.getRulesEvaluatedCount());
        assertEquals(1, result.getViolations().size());
        assertEquals("VERSION004", result.getViolations().get(0).getRuleId());
        assertEquals(99.5, result.getOverallScore());
    }

    @Test
    void rejectsAnUnknownRuleParameterWhileLoadingTheBundle() {
        Map<String, String> resources = bundledResources();
        resources.put("rules/REST001.md", resources.get("rules/REST001.md").replace("match: operation-verb", "match: operation-verb\n  banana: true"));

        BundleValidationException error = assertThrows(BundleValidationException.class,
                () -> new PolicyBundleLoader().load(resources::get));

        assertTrue(error.getMessage().contains("unknown parameter 'banana'"));
    }

    @Test
    void rejectsAMissingRequiredRuleParameterWhileLoadingTheBundle() {
        Map<String, String> resources = bundledResources();
        resources.put("rules/REST001.md", resources.get("rules/REST001.md").replace("parameters:\n  match: operation-verb\n", "parameters: {}\n"));

        BundleValidationException error = assertThrows(BundleValidationException.class,
                () -> new PolicyBundleLoader().load(resources::get));

        assertTrue(error.getMessage().contains("missing required parameter 'match'"));
    }

    @Test
    void rejectsABrokenDetectorScriptWhileLoadingTheBundle() {
        Map<String, String> resources = bundledResources();
        resources.put("detectors/resource-path/Detector.groovy", "{ api, rule -> this is not valid Groovy ) }");

        BundleValidationException error = assertThrows(BundleValidationException.class,
                () -> new PolicyBundleLoader().load(resources::get));

        assertTrue(error.getMessage().contains("does not compile"));
    }

    @Test
    void acceptsCatalogueRulesWhoseDetectorIsNotYetBundled() {
        Map<String, String> resources = bundledResources();
        resources.put("PolicyBundle.yaml", resources.get("PolicyBundle.yaml")
                .replace("  DOC009: rules/DOC009.md", "  DOC009: rules/DOC009.md\n  FUTURE001: rules/FUTURE001.md"));
        resources.put("rules/FUTURE001.md", """
                ---
                id: FUTURE001
                category: Documentation
                detector: future-detector
                scope: operation
                parameters:
                  initial-capital: false
                ---

                # FUTURE001 — A future catalogue rule
                """);

        assertDoesNotThrow(() -> new PolicyBundleLoader().load(resources::get));
    }

    @Test
    void operationDetectorReportsOperationsWhoseSummaryIsMissing() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Rule rule = bundle.rules().get("DOC001");
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(ACTION_PATH_SPEC, null, new ParseOptions()).getOpenAPI());

        java.util.List<Occurrence> occurrences = new GroovyDetectorRuntime()
                .execute(bundle.detectors().get("operation"), api, rule);

        assertEquals(3, occurrences.size());
        assertEquals("/paths/~1getAllCustomers/get", occurrences.get(0).pointer());
        assertEquals("GET /getAllCustomers", occurrences.get(0).path());
        assertEquals("Operation summary is missing", occurrences.get(0).message());
    }

    @Test
    void textStyleDetectorUsesTypedRuleParameters() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(SUMMARY_STYLE_SPEC, null, new ParseOptions()).getOpenAPI());

        java.util.List<Occurrence> initialCapital = new GroovyDetectorRuntime()
                .execute(bundle.detectors().get("text-style"), api, bundle.rules().get("DOC002"));
        java.util.List<Occurrence> tooLong = new GroovyDetectorRuntime()
                .execute(bundle.detectors().get("text-style"), api, bundle.rules().get("DOC005"));

        assertEquals(1, initialCapital.size());
        assertEquals("GET /customers", initialCapital.get(0).path());
        assertEquals(1, tooLong.size());
        assertEquals("GET /orders", tooLong.get(0).path());
    }

    @Test
    void namingDetectorInspectsStableParameterAndSchemaMaps() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(NAMING_SPEC, null, new ParseOptions()).getOpenAPI());
        GroovyDetectorRuntime runtime = new GroovyDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("CASE001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("CASE002")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("CASE003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("CASE004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("CASE005")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("REST005")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("naming"), api, bundle.rules().get("JSON004")).size());
    }

    @Test
    void schemaDetectorUsesOnlyStablePrimitivePropertyFacts() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(NAMING_SPEC, null, new ParseOptions()).getOpenAPI());
        GroovyDetectorRuntime runtime = new GroovyDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON006")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON007")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON009")).size());
    }

    @Test
    void existingDetectorsCoverTheNextFiveCataloguedRules() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(METHOD_AND_ACTION_SPEC, null, new ParseOptions()).getOpenAPI());
        GroovyDetectorRuntime runtime = new GroovyDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("resource-path"), api, bundle.rules().get("REST003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("resource-path"), api, bundle.rules().get("REST004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("HTTP004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("HTTP005")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("UPDATE002")).size());
    }

    @Test
    void operationSemanticsDetectorReportsOnlyDocumentedHeuristicSignals() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers/{customer_id}:
                    get: { summary: Delete customer, responses: { '200': { description: OK } } }
                    post: { summary: Replace customer, responses: { '200': { description: OK } } }
                    put: { summary: Partially update customer, responses: { '200': { description: OK } } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser().readContents(spec, null, new ParseOptions()).getOpenAPI());
        GroovyDetectorRuntime runtime = new GroovyDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("operation-semantics"), api, bundle.rules().get("HTTP001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation-semantics"), api, bundle.rules().get("HTTP002")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation-semantics"), api, bundle.rules().get("HTTP003")).size());
        assertEquals(2, runtime.execute(bundle.detectors().get("operation-semantics"), api, bundle.rules().get("HTTP006")).size());
        assertTrue(runtime.execute(bundle.detectors().get("operation-semantics"), api, bundle.rules().get("HTTP008")).isEmpty());
    }

    @Test
    void packagesThePolicyBundleResources() {
        assertResource("api-policy/PolicyBundle.yaml");
        assertResource("api-policy/rules/REST001.md");
        assertResource("api-policy/rules/DOC001.md");
        assertResource("api-policy/rules/DOC002.md");
        assertResource("api-policy/rules/DOC003.md");
        assertResource("api-policy/rules/DOC004.md");
        assertResource("api-policy/rules/DOC005.md");
        assertResource("api-policy/rules/DOC009.md");
        assertResource("api-policy/policies/Strict.md");
        assertResource("api-policy/policies/Mastercard.md");
        assertResource("api-policy/detectors/resource-path/Detector.md");
        assertResource("api-policy/detectors/resource-path/Detector.groovy");
        assertResource("api-policy/detectors/operation/Detector.md");
        assertResource("api-policy/detectors/operation/Detector.groovy");
        assertResource("api-policy/detectors/text-style/Detector.md");
        assertResource("api-policy/detectors/text-style/Detector.groovy");
    }

    private static SpecInput input(String content) {
        return SpecInput.builder().content(content).format(SpecFormat.OPENAPI3).ruleSet("Strict").build();
    }

    private static Map<String, String> bundledResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : new String[] {"PolicyBundle.yaml", "rules/REST001.md", "rules/REST002.md", "rules/REST003.md", "rules/REST004.md", "rules/REST005.md", "rules/REST006.md", "rules/HTTP001.md", "rules/HTTP002.md", "rules/HTTP003.md", "rules/HTTP004.md", "rules/HTTP005.md", "rules/HTTP006.md", "rules/HTTP008.md", "rules/UPDATE001.md", "rules/UPDATE002.md", "rules/UPDATE003.md", "rules/BULK001.md", "rules/BULK002.md", "rules/BULK003.md", "rules/VERSION001.md", "rules/VERSION002.md", "rules/VERSION003.md", "rules/VERSION004.md", "rules/COMPAT001.md", "rules/COMPAT002.md", "rules/COMPAT003.md", "rules/COMPAT004.md", "rules/COMPAT005.md", "rules/COMPAT006.md", "rules/STATUS001.md", "rules/STATUS002.md", "rules/STATUS003.md", "rules/STATUS004.md", "rules/STATUS005.md", "rules/DOC001.md", "rules/DOC002.md", "rules/DOC003.md", "rules/DOC004.md", "rules/DOC005.md", "rules/DOC009.md", "rules/CASE001.md", "rules/CASE002.md", "rules/CASE003.md", "rules/CASE004.md", "rules/CASE005.md", "rules/JSON003.md", "rules/JSON004.md", "rules/JSON006.md", "rules/JSON007.md", "rules/JSON009.md", "policies/Strict.md", "policies/Mastercard.md", "detectors/resource-path/Detector.md", "detectors/resource-path/Detector.groovy", "detectors/operation/Detector.md", "detectors/operation/Detector.groovy", "detectors/text-style/Detector.md", "detectors/text-style/Detector.groovy", "detectors/naming/Detector.md", "detectors/naming/Detector.groovy", "detectors/schema/Detector.md", "detectors/schema/Detector.groovy", "detectors/operation-semantics/Detector.md", "detectors/operation-semantics/Detector.groovy", "detectors/response-code/Detector.md", "detectors/response-code/Detector.groovy", "detectors/response-header/Detector.md", "detectors/response-header/Detector.groovy", "detectors/manual/Detector.md", "detectors/manual/Detector.groovy", "detectors/bulk-operation/Detector.md", "detectors/bulk-operation/Detector.groovy", "detectors/versioning/Detector.md", "detectors/versioning/Detector.groovy", "detectors/compatibility/Detector.md", "detectors/compatibility/Detector.groovy"}) {
            resources.put(path, readResource("api-policy/" + path));
        }
        return resources;
    }

    private static void assertResource(String resource) {
        try (InputStream stream = GenericPolicyValidationPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be packaged");
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }

    private static String readResource(String resource) {
        try (InputStream stream = GenericPolicyValidationPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }
}
