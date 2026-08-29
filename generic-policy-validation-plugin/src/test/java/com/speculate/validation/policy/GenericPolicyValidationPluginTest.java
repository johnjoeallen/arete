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
    void executesTheStrictPolicyAndDeductsOnlyOncePerRule() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(73, result.getRulesEvaluatedCount());
        assertEquals(22, result.getViolations().size());
        assertEquals("REST001", result.getViolations().get(0).getRuleId());
        assertEquals(2, result.getViolations().stream().filter(violation -> violation.getRuleId().equals("REST001")).count());
        assertEquals(94.5, result.getOverallScore());
        assertEquals(94.5, result.getOverallScoreWithoutBlockers());
        assertEquals(0.5, result.getViolations().get(0).getScoreImprovement());
        assertEquals(0.5, result.getViolations().get(1).getScoreImprovement());
        assertEquals("http://localhost:6809/plugins/generic-policy/rules/REST001",
                result.getViolations().get(0).getDocumentationUrl());
        assertTrue(plugin.getRuleDocumentation("REST001").orElseThrow().markdown().contains("GET /customers"));
        assertTrue(plugin.getRuleDocumentation("STANDARD005").orElseThrow().markdown().contains("2 nested resource levels"));
    }

    @Test
    void returnsAFullScoreWhenNoStrictRuleMatches() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(COMPLIANT_STARTER_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(73, result.getRulesEvaluatedCount());
        assertEquals(12, result.getViolations().size());
        assertEquals("DOC006", result.getViolations().get(0).getRuleId());
        assertEquals(96.5, result.getOverallScore());
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
    void rejectsABrokenStarlarkDetectorScriptWhileLoadingTheBundle() {
        Map<String, String> resources = bundledResources();
        resources.put("detectors/resource-path/Detector.star", "def detect(api, rule)\n    this is not valid Starlark");

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

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
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

        java.util.List<Occurrence> initialCapital = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("text-style"), api, bundle.rules().get("DOC002"));
        java.util.List<Occurrence> tooLong = new StarlarkDetectorRuntime()
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
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

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
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON006")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON007")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("schema"), api, bundle.rules().get("JSON009")).size());
    }

    @Test
    void existingDetectorsCoverTheNextFiveCataloguedRules() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(METHOD_AND_ACTION_SPEC, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("resource-path"), api, bundle.rules().get("REST003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("resource-path"), api, bundle.rules().get("REST004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("HTTP004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("HTTP005")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("operation"), api, bundle.rules().get("UPDATE002")).size());
    }

    @Test
    void proprietaryHeaderDetectorReportsOnlyNonAllowListedCustomHeaders() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      parameters:
                        - { name: X-Internal-Trace, in: header, schema: { type: string } }
                        - { name: X-Request-Id, in: header, schema: { type: string } }
                      responses:
                        '200':
                          description: OK
                          headers:
                            X-Internal-Region: { schema: { type: string } }
                            Content-Type: { schema: { type: string } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("proprietary-header"), api, bundle.rules().get("STANDARD008"));

        assertEquals(2, occurrences.size());
        assertTrue(occurrences.stream().anyMatch(o -> o.message().contains("X-Internal-Trace")));
        assertTrue(occurrences.stream().anyMatch(o -> o.message().contains("X-Internal-Region")));
    }

    @Test
    void policiesCanOverrideRuleParametersIndependently() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));

        assertEquals("X-Request-Id", bundle.policies().get("Zalando").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
        assertEquals("X-Request-Id,X-Correlation-Id,X-Trace-Id", bundle.policies().get("Enterprise Grade").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
        assertEquals("X-Request-Id,X-Correlation-Id", bundle.policies().get("Zalando Extended").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
    }

    @Test
    void queryCollectionDetectorHonoursConfiguredSerialization() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      parameters:
                        - name: tags
                          in: query
                          style: form
                          explode: true
                          schema: { type: array, items: { type: string } }
                        - name: states
                          in: query
                          style: pipeDelimited
                          explode: false
                          schema: { type: array, items: { type: string } }
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("query-collection"), api, bundle.rules().get("STANDARD009"));

        assertEquals(1, occurrences.size());
        assertTrue(occurrences.get(0).message().contains("states"));
    }

    @Test
    void errorResponseDetectorChecksCoverageAndProtocolDetails() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      responses:
                        '200': { description: OK }
                        '401': { description: Unauthorized }
                        '405':
                          description: Method not allowed
                          headers: { Allow: { schema: { type: string } } }
                          content: { application/problem+json: { schema: { type: object } } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("error-response"), api, bundle.rules().get("ERROR003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("error-response"), api, bundle.rules().get("ERROR005")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("error-response"), api, bundle.rules().get("ERROR006")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("error-response"), api, bundle.rules().get("ERROR007")).size());
    }

    @Test
    void authenticationErrorDetectorUsesEffectiveSecurityRequirements() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                security: [ { bearerAuth: [] } ]
                paths:
                  /customers:
                    get:
                      responses:
                        '200': { description: OK }
                        '401': { description: Unauthorized, headers: { WWW-Authenticate: { schema: { type: string } } } }
                        '403': { description: Forbidden, headers: { WWW-Authenticate: { schema: { type: string } } } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(0, runtime.execute(bundle.detectors().get("authentication-error"), api, bundle.rules().get("ERROR008")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("authentication-error"), api, bundle.rules().get("ERROR009")).size());
        java.util.List<Occurrence> forbiddenChallenge = runtime.execute(bundle.detectors().get("authentication-error"), api, bundle.rules().get("ERROR010"));
        assertTrue(!forbiddenChallenge.isEmpty(), forbiddenChallenge.toString());
    }

    @Test
    void sensitiveDataDetectorChecksConfigurableNameLocations() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /accounts/{access_token}:
                    parameters:
                      - { name: access_token, in: path, required: true, schema: { type: string } }
                    get:
                      parameters:
                        - { name: password, in: query, schema: { type: string } }
                        - { name: X-Api-Key, in: header, schema: { type: string } }
                      responses: { '200': { description: OK } }
                components:
                  schemas:
                    Account:
                      type: object
                      properties:
                        password: { type: string }
                        displayName: { type: string }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("sensitive-data"), api, bundle.rules().get("SEC001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("sensitive-data"), api, bundle.rules().get("SEC002")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("sensitive-data"), api, bundle.rules().get("SEC003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("sensitive-data"), api, bundle.rules().get("SEC008")).size());
    }

    @Test
    void sensitiveSearchDetectorDistinguishesSearchInputsFromSensitiveFields() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /accounts:
                    get:
                      parameters:
                        - { name: search, in: query, schema: { type: string } }
                        - { name: ssn, in: query, schema: { type: string } }
                      responses: { '200': { description: OK } }
                  /public:
                    get:
                      parameters:
                        - { name: q, in: query, schema: { type: string } }
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(2, runtime.execute(bundle.detectors().get("sensitive-search"), api, bundle.rules().get("SEC004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("sensitive-search"), api, bundle.rules().get("SEC005")).size());
    }

    @Test
    void identifierDetectorChecksTypeAndFormatSeparately() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    Customer:
                      type: object
                      properties:
                        id: { type: integer }
                        customer_id: { type: string }
                        displayName: { type: string }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(1, runtime.execute(bundle.detectors().get("identifier"), api, bundle.rules().get("SEC006")).size());
        assertEquals(2, runtime.execute(bundle.detectors().get("identifier"), api, bundle.rules().get("SEC007")).size());
    }

    @Test
    void collectionCapabilityDetectorChecksPresenceAndRepresentation() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      parameters:
                        - { name: filter, in: query, schema: { type: integer } }
                        - { name: sort, in: query, style: pipeDelimited, schema: { type: array, items: { type: string } } }
                        - { name: fields, in: query, schema: { type: string } }
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(0, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("FILTER001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("FILTER002")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("SORT001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("SORT002")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("SORT003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("SORT004")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("collection-capability"), api, bundle.rules().get("FIELD001")).size());
    }

    @Test
    void paginationDetectorChecksControlsLimitsAndLinks() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      parameters:
                        - { name: page, in: query, schema: { type: string } }
                        - { name: limit, in: query, schema: { type: integer, maximum: 200 } }
                        - { name: cursor, in: query, schema: { type: string } }
                      responses:
                        '200': { description: OK }
                  /orders/{orderId}:
                    get:
                      parameters:
                        - { name: orderId, in: path, required: true, schema: { type: string } }
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertEquals(0, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE001")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE002")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE003")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE004")).size());
        assertEquals(1, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE005")).size());
        assertEquals(0, runtime.execute(bundle.detectors().get("pagination"), api, bundle.rules().get("PAGE006")).size());
    }

    @Test
    void securityDetectorUsesOperationSecurityAndGlobalFallback() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                security:
                  - bearerAuth: []
                paths:
                  /customers:
                    get:
                      responses: { '200': { description: OK } }
                  /public:
                    get:
                      security: []
                      responses: { '200': { description: OK } }
                  /admin:
                    get:
                      security:
                        - apiKey: []
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("security"), api, bundle.rules().get("SECURITY001"));

        assertEquals(2, occurrences.size());
        assertTrue(occurrences.stream().anyMatch(o -> o.path().equals("GET /public")));
        assertTrue(occurrences.stream().anyMatch(o -> o.path().equals("GET /admin")));
    }

    @Test
    void securityDetectorRequiresAllConfiguredScopes() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                security:
                  - bearerAuth: [read, write]
                paths:
                  /customers:
                    get:
                      responses: { '200': { description: OK } }
                  /orders:
                    get:
                      security:
                        - bearerAuth: [read]
                      responses: { '200': { description: OK } }
                  /admin:
                    get:
                      security:
                        - bearerAuth: [read, write]
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        Rule rule = bundle.rules().get("SECURITY002");
        Rule writeRule = new Rule(rule.id(), rule.title(), rule.category(), rule.detector(), rule.scope(),
                Map.of("scheme", "bearerAuth", "scopes", "read,write"), rule.documentationMarkdown());

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("security"), api, writeRule);

        assertEquals(1, occurrences.size());
        assertTrue(occurrences.get(0).path().equals("GET /orders"));
    }

    @Test
    void responseHeaderDetectorRequiresEveryConfiguredRateLimitHeader() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      responses:
                        '429':
                          description: Too many requests
                          headers:
                            RateLimit-Limit: { schema: { type: integer } }
                            RateLimit-Remaining: { schema: { type: integer } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("response-header"), api, bundle.rules().get("STATUS007"));

        assertEquals(1, occurrences.size());
        assertTrue(occurrences.get(0).message().contains("RateLimit-Reset"));
    }

    @Test
    void openApiVersionDetectorChecksPolicySupportedVersionPrefixes() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.3
                info: { title: Test API, version: 1.0.0 }
                paths: {}
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        Rule rule = bundle.rules().get("STANDARD010");
        Rule strictVersionRule = new Rule(rule.id(), rule.title(), rule.category(), rule.detector(), rule.scope(),
                Map.of("allowed", "3.1"), rule.documentationMarkdown());

        java.util.List<Occurrence> occurrences = new StarlarkDetectorRuntime()
                .execute(bundle.detectors().get("openapi-version"), api, strictVersionRule);

        assertEquals(1, occurrences.size());
        assertTrue(occurrences.get(0).message().contains("3.0.3"));
    }

    @Test
    void mediaTypeDetectorChecksRequestAndResponseContentFacts() {
        PolicyBundle bundle = new PolicyBundleLoader().load(new ClasspathBundleResources(getClass().getClassLoader()));
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    post:
                      requestBody:
                        content:
                          application/json: { schema: { type: object } }
                      responses:
                        '200':
                          description: OK
                          content:
                            application/*: { schema: { type: object } }
                        '201':
                          description: Created
                          content:
                            application/xml: { schema: { type: object } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

        assertTrue(runtime.execute(bundle.detectors().get("media-type"), api, bundle.rules().get("CONTENT001")).isEmpty());
        assertEquals(1, runtime.execute(bundle.detectors().get("media-type"), api, bundle.rules().get("CONTENT003")).size());
        assertEquals(2, runtime.execute(bundle.detectors().get("media-type"), api, bundle.rules().get("CONTENT004")).size());
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
        StarlarkDetectorRuntime runtime = new StarlarkDetectorRuntime();

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
        assertResource("api-policy/rules/STANDARD008.md");
        assertResource("api-policy/rules/STANDARD009.md");
        assertResource("api-policy/rules/SECURITY001.md");
        assertResource("api-policy/rules/SECURITY002.md");
        assertResource("api-policy/rules/STATUS007.md");
        assertResource("api-policy/rules/STANDARD010.md");
        assertResource("api-policy/rules/CONTENT001.md");
        assertResource("api-policy/rules/CONTENT002.md");
        assertResource("api-policy/rules/CONTENT003.md");
        assertResource("api-policy/rules/CONTENT004.md");
        assertResource("api-policy/policies/Strict.md");
        assertResource("api-policy/policies/EnterpriseGrade.md");
        assertResource("api-policy/detectors/resource-path/Detector.md");
        assertResource("api-policy/detectors/resource-path/Detector.star");
        assertResource("api-policy/detectors/schema/Detector.star");
        assertResource("api-policy/detectors/security/Detector.star");
        assertResource("api-policy/detectors/operation/Detector.md");
        assertResource("api-policy/detectors/text-style/Detector.md");
        assertResource("api-policy/detectors/proprietary-header/Detector.md");
        assertResource("api-policy/detectors/query-collection/Detector.md");
        assertResource("api-policy/detectors/security/Detector.md");
        assertResource("api-policy/detectors/openapi-version/Detector.md");
        assertResource("api-policy/detectors/media-type/Detector.md");
        assertResource("api-policy/detectors/error-response/Detector.md");
        assertResource("api-policy/detectors/error-response/Detector.star");
    }

    private static SpecInput input(String content) {
        return SpecInput.builder().content(content).format(SpecFormat.OPENAPI3).ruleSet("Strict").build();
    }

    private static Map<String, String> bundledResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : new String[] {"PolicyBundle.yaml", "rules/REST001.md", "rules/REST002.md", "rules/REST003.md", "rules/REST004.md", "rules/REST005.md", "rules/REST006.md", "rules/HTTP001.md", "rules/HTTP002.md", "rules/HTTP003.md", "rules/HTTP004.md", "rules/HTTP005.md", "rules/HTTP006.md", "rules/HTTP008.md", "rules/UPDATE001.md", "rules/UPDATE002.md", "rules/UPDATE003.md", "rules/BULK001.md", "rules/BULK002.md", "rules/BULK003.md", "rules/VERSION001.md", "rules/VERSION002.md", "rules/VERSION003.md", "rules/VERSION004.md", "rules/COMPAT001.md", "rules/COMPAT002.md", "rules/COMPAT003.md", "rules/COMPAT004.md", "rules/COMPAT005.md", "rules/COMPAT006.md", "rules/STATUS001.md", "rules/STATUS002.md", "rules/STATUS003.md", "rules/STATUS004.md", "rules/STATUS005.md", "rules/STATUS006.md", "rules/STATUS007.md", "rules/DOC001.md", "rules/DOC002.md", "rules/DOC003.md", "rules/DOC004.md", "rules/DOC005.md", "rules/DOC006.md", "rules/DOC009.md", "rules/CASE001.md", "rules/CASE002.md", "rules/CASE003.md", "rules/CASE004.md", "rules/CASE005.md", "rules/JSON003.md", "rules/JSON004.md", "rules/JSON006.md", "rules/JSON007.md", "rules/JSON009.md", "rules/STANDARD008.md", "rules/STANDARD009.md", "rules/SECURITY001.md", "rules/SECURITY002.md", "rules/ERROR001.md", "rules/ERROR002.md", "rules/ERROR003.md", "rules/ERROR004.md", "rules/ERROR005.md", "rules/ERROR006.md", "rules/ERROR007.md", "policies/Strict.md", "policies/EnterpriseGrade.md", "detectors/resource-path/Detector.md", "detectors/resource-path/Detector.groovy", "detectors/operation/Detector.md", "detectors/operation/Detector.groovy", "detectors/text-style/Detector.md", "detectors/text-style/Detector.groovy", "detectors/naming/Detector.md", "detectors/naming/Detector.groovy", "detectors/schema/Detector.md", "detectors/schema/Detector.groovy", "detectors/operation-semantics/Detector.md", "detectors/operation-semantics/Detector.groovy", "detectors/response-code/Detector.md", "detectors/response-code/Detector.groovy", "detectors/response-header/Detector.md", "detectors/response-header/Detector.groovy", "detectors/proprietary-header/Detector.md", "detectors/proprietary-header/Detector.groovy", "detectors/query-collection/Detector.md", "detectors/query-collection/Detector.groovy", "detectors/security/Detector.md", "detectors/security/Detector.groovy", "detectors/manual/Detector.md", "detectors/manual/Detector.groovy", "detectors/bulk-operation/Detector.md", "detectors/bulk-operation/Detector.groovy", "detectors/versioning/Detector.md", "detectors/versioning/Detector.groovy", "detectors/compatibility/Detector.md", "detectors/compatibility/Detector.groovy", "detectors/metadata/Detector.md", "detectors/metadata/Detector.groovy", "detectors/error-response/Detector.md", "detectors/error-response/Detector.groovy"}) {
            if (path.endsWith("Detector.groovy")) continue;
            resources.put(path, readResource("api-policy/" + path));
        }
        resources.put("rules/STANDARD010.md", readResource("api-policy/rules/STANDARD010.md"));
        resources.put("detectors/openapi-version/Detector.md", readResource("api-policy/detectors/openapi-version/Detector.md"));
        resources.put("rules/CONTENT001.md", readResource("api-policy/rules/CONTENT001.md"));
        resources.put("rules/CONTENT002.md", readResource("api-policy/rules/CONTENT002.md"));
        resources.put("rules/CONTENT003.md", readResource("api-policy/rules/CONTENT003.md"));
        resources.put("rules/CONTENT004.md", readResource("api-policy/rules/CONTENT004.md"));
        resources.put("detectors/media-type/Detector.md", readResource("api-policy/detectors/media-type/Detector.md"));
        resources.put("rules/DOC007.md", readResource("api-policy/rules/DOC007.md"));
        resources.put("rules/DOC008.md", readResource("api-policy/rules/DOC008.md"));
        resources.put("rules/STANDARD001.md", readResource("api-policy/rules/STANDARD001.md"));
        resources.put("rules/STANDARD002.md", readResource("api-policy/rules/STANDARD002.md"));
        resources.put("rules/STANDARD003.md", readResource("api-policy/rules/STANDARD003.md"));
        resources.put("rules/STANDARD004.md", readResource("api-policy/rules/STANDARD004.md"));
        resources.put("rules/STANDARD005.md", readResource("api-policy/rules/STANDARD005.md"));
        resources.put("rules/STANDARD006.md", readResource("api-policy/rules/STANDARD006.md"));
        resources.put("rules/STANDARD007.md", readResource("api-policy/rules/STANDARD007.md"));
        resources.put("rules/JSON010.md", readResource("api-policy/rules/JSON010.md"));
        resources.put("rules/JSON011.md", readResource("api-policy/rules/JSON011.md"));
        resources.put("rules/JSON012.md", readResource("api-policy/rules/JSON012.md"));
        resources.put("rules/JSON013.md", readResource("api-policy/rules/JSON013.md"));
        resources.put("rules/JSON014.md", readResource("api-policy/rules/JSON014.md"));
        resources.put("rules/JSON015.md", readResource("api-policy/rules/JSON015.md"));
        resources.put("rules/JSON016.md", readResource("api-policy/rules/JSON016.md"));
        resources.put("rules/STATUS006.md", readResource("api-policy/rules/STATUS006.md"));
        resources.put("detectors/date-time-name/Detector.md", readResource("api-policy/detectors/date-time-name/Detector.md"));
        resources.put("detectors/common-field/Detector.md", readResource("api-policy/detectors/common-field/Detector.md"));
        resources.put("detectors/path-count/Detector.md", readResource("api-policy/detectors/path-count/Detector.md"));
        resources.put("policies/Zalando.md", readResource("api-policy/policies/Zalando.md"));
        resources.put("policies/ZalandoExtended.md", readResource("api-policy/policies/ZalandoExtended.md"));
        resources.put("detectors/hostname/Detector.md", readResource("api-policy/detectors/hostname/Detector.md"));
        resources.put("rules/ERROR008.md", readResource("api-policy/rules/ERROR008.md"));
        resources.put("rules/ERROR009.md", readResource("api-policy/rules/ERROR009.md"));
        resources.put("rules/ERROR010.md", readResource("api-policy/rules/ERROR010.md"));
        resources.put("detectors/authentication-error/Detector.md", readResource("api-policy/detectors/authentication-error/Detector.md"));
        resources.put("rules/SEC001.md", readResource("api-policy/rules/SEC001.md"));
        resources.put("rules/SEC002.md", readResource("api-policy/rules/SEC002.md"));
        resources.put("rules/SEC003.md", readResource("api-policy/rules/SEC003.md"));
        resources.put("rules/SEC008.md", readResource("api-policy/rules/SEC008.md"));
        resources.put("detectors/sensitive-data/Detector.md", readResource("api-policy/detectors/sensitive-data/Detector.md"));
        resources.put("rules/SEC004.md", readResource("api-policy/rules/SEC004.md"));
        resources.put("rules/SEC005.md", readResource("api-policy/rules/SEC005.md"));
        resources.put("detectors/sensitive-search/Detector.md", readResource("api-policy/detectors/sensitive-search/Detector.md"));
        resources.put("rules/SEC006.md", readResource("api-policy/rules/SEC006.md"));
        resources.put("rules/SEC007.md", readResource("api-policy/rules/SEC007.md"));
        resources.put("detectors/identifier/Detector.md", readResource("api-policy/detectors/identifier/Detector.md"));
        resources.put("rules/FILTER001.md", readResource("api-policy/rules/FILTER001.md"));
        resources.put("rules/FILTER002.md", readResource("api-policy/rules/FILTER002.md"));
        resources.put("rules/SORT001.md", readResource("api-policy/rules/SORT001.md"));
        resources.put("rules/SORT002.md", readResource("api-policy/rules/SORT002.md"));
        resources.put("rules/SORT003.md", readResource("api-policy/rules/SORT003.md"));
        resources.put("rules/SORT004.md", readResource("api-policy/rules/SORT004.md"));
        resources.put("rules/FIELD001.md", readResource("api-policy/rules/FIELD001.md"));
        resources.put("detectors/collection-capability/Detector.md", readResource("api-policy/detectors/collection-capability/Detector.md"));
        resources.put("rules/PAGE001.md", readResource("api-policy/rules/PAGE001.md"));
        resources.put("rules/PAGE002.md", readResource("api-policy/rules/PAGE002.md"));
        resources.put("rules/PAGE003.md", readResource("api-policy/rules/PAGE003.md"));
        resources.put("rules/PAGE004.md", readResource("api-policy/rules/PAGE004.md"));
        resources.put("rules/PAGE005.md", readResource("api-policy/rules/PAGE005.md"));
        resources.put("rules/PAGE006.md", readResource("api-policy/rules/PAGE006.md"));
        resources.put("detectors/pagination/Detector.md", readResource("api-policy/detectors/pagination/Detector.md"));
        for (String detectorId : new String[] {"resource-path", "operation", "text-style", "naming", "schema",
                "operation-semantics", "response-code", "response-header", "proprietary-header", "query-collection",
                "security", "manual", "bulk-operation", "versioning", "compatibility", "metadata", "openapi-version",
                "media-type", "date-time-name", "common-field", "path-count", "hostname", "error-response", "authentication-error", "sensitive-data", "sensitive-search", "identifier", "collection-capability", "pagination"}) {
            resources.put("detectors/" + detectorId + "/Detector.star",
                    readResource("api-policy/detectors/" + detectorId + "/Detector.star"));
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
