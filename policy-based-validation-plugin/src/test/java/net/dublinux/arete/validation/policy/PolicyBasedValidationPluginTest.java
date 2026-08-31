package net.dublinux.arete.validation.policy;

import net.dublinux.arete.validation.spi.SpecFormat;
import net.dublinux.arete.validation.spi.SpecInput;
import net.dublinux.arete.validation.spi.ValidationResult;
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

class PolicyBasedValidationPluginTest {
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
    void executesTheEnterpriseGradePolicyAndDeductsOnlyOncePerRule() {
        PolicyBasedValidationPlugin plugin = new PolicyBasedValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(109, result.getRulesEvaluatedCount());
        assertEquals(32, result.getDiagnostics().size());
        assertEquals("REST001", result.getDiagnostics().get(0).getRuleId());
        assertEquals(2, result.getDiagnostics().stream().filter(diagnostic -> diagnostic.getRuleId().equals("REST001")).count());
        assertEquals(92.5, result.getOverallScore());
        assertEquals(92.5, result.getOverallScoreWithoutBlockers());
        assertEquals("B", result.getGrade());                       // 92.5 -> B (bands A:95 B:90 C:80 D:70)
        assertEquals(90.0, plugin.getPassingScore("Enterprise Grade").orElseThrow());
        assertEquals("score<90", plugin.getSuggestedScoreLevel("Enterprise Grade").orElseThrow());
        assertEquals(0.5, result.getDiagnostics().get(0).getScoreImprovement());
        assertEquals(0.5, result.getDiagnostics().get(1).getScoreImprovement());
        assertEquals("http://localhost:6809/plugins/generic-policy/rules/REST001",
                result.getDiagnostics().get(0).getDocumentationUrl());
        assertTrue(plugin.getRuleDocumentation("REST001").orElseThrow().markdown().contains("GET /customers"));
        assertTrue(plugin.getRuleDocumentation("STANDARD005").orElseThrow().markdown().contains("2 nested resource levels"));
    }

    @Test
    void returnsAFullScoreWhenNoRuleMatches() {
        PolicyBasedValidationPlugin plugin = new PolicyBasedValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(COMPLIANT_STARTER_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(109, result.getRulesEvaluatedCount());
        assertEquals(19, result.getDiagnostics().size());
        assertEquals("DOC006", result.getDiagnostics().get(0).getRuleId());
        assertEquals(94.5, result.getOverallScore());
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
    void acceptsCatalogueRulesWhoseRuleIsNotYetBundled() {
        Map<String, String> resources = bundledResources();
        resources.put("PolicyBundle.yaml", resources.get("PolicyBundle.yaml")
                .replace("  DOC009: rules/DOC009.md", "  DOC009: rules/DOC009.md\n  FUTURE001: rules/FUTURE001.md"));
        resources.put("rules/FUTURE001.md", """
                ---
                id: FUTURE001
                category: Documentation
                matcher: future-rule
                scope: operation
                parameters:
                  initial-capital: false
                ---

                # FUTURE001 — A future catalogue rule
                """);

        assertDoesNotThrow(() -> new PolicyBundleLoader().load(resources::get));
    }

    @Test
    void operationRuleReportsOperationsWhoseSummaryIsMissing() {
        PolicyBundle bundle = distillBundle();
        PolicyRule rule = bundle.rules().get("DOC001");
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(ACTION_PATH_SPEC, null, new ParseOptions()).getOpenAPI());

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("operation"), api, rule);

        assertEquals(3, diagnostics.size());
        assertEquals("/paths/~1getAllCustomers/get", diagnostics.get(0).pointer());
        assertEquals("GET /getAllCustomers", diagnostics.get(0).path());
        assertEquals("Operation summary is missing", diagnostics.get(0).message());
    }

    @Test
    void textStyleRuleUsesTypedRuleParameters() {
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(SUMMARY_STYLE_SPEC, null, new ParseOptions()).getOpenAPI());

        java.util.List<Diagnostic> initialCapital = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("text-style"), api, bundle.rules().get("DOC002"));
        java.util.List<Diagnostic> tooLong = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("text-style"), api, bundle.rules().get("DOC005"));

        assertEquals(1, initialCapital.size());
        assertEquals("GET /customers", initialCapital.get(0).path());
        assertEquals(1, tooLong.size());
        assertEquals("GET /orders", tooLong.get(0).path());
    }

    @Test
    void namingRuleInspectsStableParameterAndSchemaMaps() {
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(NAMING_SPEC, null, new ParseOptions()).getOpenAPI());
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("CASE001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("CASE002")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("CASE003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("CASE004")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("CASE005")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("REST005")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("naming"), api, bundle.rules().get("JSON004")).size());
    }

    @Test
    void schemaRuleUsesOnlyStablePrimitivePropertyFacts() {
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(NAMING_SPEC, null, new ParseOptions()).getOpenAPI());
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("schema"), api, bundle.rules().get("JSON006")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("schema"), api, bundle.rules().get("JSON007")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("schema"), api, bundle.rules().get("JSON009")).size());
    }

    @Test
    void newRulesCoverTagSchemaAndStructureChecks() {
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                tags:
                  - name: orders
                paths:
                  /v1/orders:
                    get:
                      tags: [Order Management]
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema: { $ref: '#/components/schemas/Used' }
                  /v1/customers:
                    get:
                      responses: { '200': { description: OK } }
                components:
                  schemas:
                    Used:
                      type: object
                      properties:
                        amount: { type: number }
                        note: { type: string }
                    Unused:
                      type: object
                """;
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI(), java.util.List.of(), spec);
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("path-prefix"), api, bundle.rules().get("REST007")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("schema"), api, bundle.rules().get("JSON021")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("schema"), api, bundle.rules().get("JSON022")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("tag"), api, bundle.rules().get("CASE008")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("tag"), api, bundle.rules().get("DOC017")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("tag"), api, bundle.rules().get("DOC018")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("component-usage"), api, bundle.rules().get("STANDARD024")).size());
    }

    @Test
    void spectralDerivedRulesFlagValidityAndSafetyIssues() {
        String spec = """
                openapi: 3.0.0
                info:
                  title: Test API
                  version: 1.0.0
                  description: "Notes <script>alert(1)</script>"
                servers:
                  - url: https://api.example.com/v1/
                tags:
                  - { name: orders, description: Order operations }
                  - { name: orders, description: Duplicate }
                paths:
                  /orders/{orderId}:
                    parameters:
                      - { name: orderId, in: path, required: true, schema: { type: string } }
                    get:
                      parameters:
                        - { name: orderId, in: path, required: true, schema: { type: string } }
                      security:
                        - undefinedScheme: []
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema: { $ref: '#/components/schemas/Order' }
                components:
                  schemas:
                    Order:
                      type: object
                      properties:
                        tags: { type: array }
                """;
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI(), java.util.List.of(), spec);
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("array-items"), api, bundle.rules().get("JSON023")).size(), "JSON023");
        assertEquals(1, runtime.execute(bundle.matchers().get("security-scheme"), api, bundle.rules().get("SECURITY003")).size(), "SECURITY003");
        assertEquals(1, runtime.execute(bundle.matchers().get("markdown-safety"), api, bundle.rules().get("SECURITY004")).size(), "SECURITY004");
        assertEquals(1, runtime.execute(bundle.matchers().get("parameter"), api, bundle.rules().get("STANDARD026")).size(), "STANDARD026");
        assertEquals(1, runtime.execute(bundle.matchers().get("server-url"), api, bundle.rules().get("STANDARD027")).size(), "STANDARD027");
        assertEquals(1, runtime.execute(bundle.matchers().get("server-url"), api, bundle.rules().get("STANDARD028")).size(), "STANDARD028");
        assertEquals(1, runtime.execute(bundle.matchers().get("tag"), api, bundle.rules().get("DOC019")).size(), "DOC019");
        assertEquals(1, runtime.execute(bundle.matchers().get("metadata"), api, bundle.rules().get("DOC020")).size(), "DOC020");
    }

    @Test
    void existingRulesCoverTheNextFiveCataloguedRules() {
        PolicyBundle bundle = distillBundle();
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(METHOD_AND_ACTION_SPEC, null, new ParseOptions()).getOpenAPI());
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("resource-path"), api, bundle.rules().get("REST003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("resource-path"), api, bundle.rules().get("REST004")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("operation"), api, bundle.rules().get("HTTP004")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("operation"), api, bundle.rules().get("HTTP005")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("operation"), api, bundle.rules().get("UPDATE002")).size());
    }

    @Test
    void proprietaryHeaderRuleReportsOnlyNonAllowListedCustomHeaders() {
        PolicyBundle bundle = distillBundle();
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

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("proprietary-header"), api, bundle.rules().get("STANDARD008"));

        assertEquals(2, diagnostics.size());
        assertTrue(diagnostics.stream().anyMatch(o -> o.message().contains("X-Internal-Trace")));
        assertTrue(diagnostics.stream().anyMatch(o -> o.message().contains("X-Internal-Region")));
    }

    @Test
    void policiesCanOverrideRuleParametersIndependently() {
        PolicyBundle bundle = distillBundle();

        assertEquals("X-Request-Id", bundle.policies().get("Zalando").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
        assertEquals("X-Request-Id,X-Correlation-Id,X-Trace-Id", bundle.policies().get("Enterprise Grade").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
        assertEquals("X-Request-Id,X-Correlation-Id", bundle.policies().get("Zalando Extended").dispositions()
                .get("STANDARD008").parameters().get("allowed"));
    }

    @Test
    void queryCollectionRuleHonoursConfiguredSerialization() {
        PolicyBundle bundle = distillBundle();
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      parameters:
                        - name: states
                          in: query
                          style: pipeDelimited
                          explode: false
                          schema: { type: array, items: { type: string } }
                      responses: { '200': { description: OK } }
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("query-collection"), api, bundle.rules().get("STANDARD009"));

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).message().contains("states"));
    }

    @Test
    void errorResponseRuleChecksCoverageAndProtocolDetails() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("error-response"), api, bundle.rules().get("ERROR003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("error-response"), api, bundle.rules().get("ERROR005")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("error-response"), api, bundle.rules().get("ERROR006")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("error-response"), api, bundle.rules().get("ERROR007")).size());
    }

    @Test
    void authenticationErrorRuleUsesEffectiveSecurityRequirements() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(0, runtime.execute(bundle.matchers().get("authentication-error"), api, bundle.rules().get("ERROR008")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("authentication-error"), api, bundle.rules().get("ERROR009")).size());
        java.util.List<Diagnostic> forbiddenChallenge = runtime.execute(bundle.matchers().get("authentication-error"), api, bundle.rules().get("ERROR010"));
        assertTrue(!forbiddenChallenge.isEmpty(), forbiddenChallenge.toString());
    }

    @Test
    void sensitiveDataRuleChecksConfigurableNameLocations() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("sensitive-data"), api, bundle.rules().get("SEC001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("sensitive-data"), api, bundle.rules().get("SEC002")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("sensitive-data"), api, bundle.rules().get("SEC003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("sensitive-data"), api, bundle.rules().get("SEC008")).size());
    }

    @Test
    void sensitiveSearchRuleDistinguishesSearchInputsFromSensitiveFields() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(2, runtime.execute(bundle.matchers().get("sensitive-search"), api, bundle.rules().get("SEC004")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("sensitive-search"), api, bundle.rules().get("SEC005")).size());
    }

    @Test
    void identifierRuleChecksTypeAndFormatSeparately() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("identifier"), api, bundle.rules().get("SEC006")).size());
        assertEquals(2, runtime.execute(bundle.matchers().get("identifier"), api, bundle.rules().get("SEC007")).size());
    }

    @Test
    void collectionCapabilityRuleChecksPresenceAndRepresentation() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(0, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("FILTER001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("FILTER002")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("SORT001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("SORT002")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("SORT003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("SORT004")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("collection-capability"), api, bundle.rules().get("FIELD001")).size());
    }

    @Test
    void paginationRuleChecksControlsLimitsAndLinks() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(0, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE002")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE003")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE004")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE005")).size());
        assertEquals(0, runtime.execute(bundle.matchers().get("pagination"), api, bundle.rules().get("PAGE006")).size());
    }

    @Test
    void securityRuleUsesOperationSecurityAndGlobalFallback() {
        PolicyBundle bundle = distillBundle();
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

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("security"), api, bundle.rules().get("SECURITY001"));

        assertEquals(2, diagnostics.size());
        assertTrue(diagnostics.stream().anyMatch(o -> o.path().equals("GET /public")));
        assertTrue(diagnostics.stream().anyMatch(o -> o.path().equals("GET /admin")));
    }

    @Test
    void securityRuleRequiresAllConfiguredScopes() {
        PolicyBundle bundle = distillBundle();
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
        PolicyRule rule = bundle.rules().get("SECURITY002");
        PolicyRule writeRule = new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(),
                Map.of("scheme", "bearerAuth", "scopes", "read,write"), rule.documentationMarkdown());

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("security"), api, writeRule);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).path().equals("GET /orders"));
    }

    @Test
    void responseHeaderRuleRequiresEveryConfiguredRateLimitHeader() {
        PolicyBundle bundle = distillBundle();
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

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("response-header"), api, bundle.rules().get("STATUS007"));

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).message().contains("RateLimit-Reset"));
    }

    @Test
    void openApiVersionRuleChecksPolicySupportedVersionPrefixes() {
        PolicyBundle bundle = distillBundle();
        String spec = """
                openapi: 3.0.3
                info: { title: Test API, version: 1.0.0 }
                paths: {}
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        PolicyRule rule = bundle.rules().get("STANDARD010");
        PolicyRule strictVersionRule = new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(),
                Map.of("allowed", "3.1"), rule.documentationMarkdown());

        java.util.List<Diagnostic> diagnostics = new DistillMatcherEvaluator()
                .execute(bundle.matchers().get("openapi-version"), api, strictVersionRule);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).message().contains("3.0.3"));
    }

    @Test
    void mediaTypeRuleChecksRequestAndResponseContentFacts() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertTrue(runtime.execute(bundle.matchers().get("media-type"), api, bundle.rules().get("CONTENT001")).isEmpty());
        assertEquals(1, runtime.execute(bundle.matchers().get("media-type"), api, bundle.rules().get("CONTENT003")).size());
        assertEquals(2, runtime.execute(bundle.matchers().get("media-type"), api, bundle.rules().get("CONTENT004")).size());
    }

    @Test
    void schemaRulesCoverEnumFormatsAndDateTimeNamingRules() {
        PolicyBundle bundle = distillBundle();
        String spec = """
                openapi: 3.0.0
                info: { title: Test API, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    Example:
                      type: object
                      properties:
                        amount:
                          type: number
                        formattedAmount:
                          type: number
                          format: double
                        mode:
                          type: string
                          enum: [pending, READY]
                          x-extensible-enum: true
                        count:
                          type: integer
                          format: int32
                          enum: [1, TWO]
                        state:
                          type: string
                          enum: [PENDING, READY]
                          x-extensible-enum: true
                        created:
                          type: string
                          format: date-time
                        created_at:
                          type: string
                          format: date-time
                """;
        Map<String, Object> api = OpenApiMapAdapter.toMap(new OpenAPIV3Parser()
                .readContents(spec, null, new ParseOptions()).getOpenAPI());
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        java.util.List<Diagnostic> missingFormat = runtime.execute(
                bundle.matchers().get("schema"), api, bundle.rules().get("JSON012"));
        assertEquals(1, missingFormat.size());
        assertEquals("amount", missingFormat.get(0).path());

        java.util.List<Diagnostic> inconsistentEnum = runtime.execute(
                bundle.matchers().get("schema"), api, bundle.rules().get("JSON013"));
        assertEquals(1, inconsistentEnum.size());
        assertEquals("count", inconsistentEnum.get(0).path());

        java.util.List<Diagnostic> nonExtensibleEnum = runtime.execute(
                bundle.matchers().get("schema"), api, bundle.rules().get("JSON014"));
        assertEquals(1, nonExtensibleEnum.size());
        assertEquals("count", nonExtensibleEnum.get(0).path());

        java.util.List<Diagnostic> nonUpperSnakeEnum = runtime.execute(
                bundle.matchers().get("schema"), api, bundle.rules().get("JSON015"));
        assertEquals(1, nonUpperSnakeEnum.size());
        assertEquals("mode", nonUpperSnakeEnum.get(0).path());

        java.util.List<Diagnostic> incorrectlyNamedDateTime = runtime.execute(
                bundle.matchers().get("date-time-name"), api, bundle.rules().get("JSON011"));
        assertEquals(1, incorrectlyNamedDateTime.size());
        assertEquals("created", incorrectlyNamedDateTime.get(0).path());
    }

    @Test
    void operationSemanticsRuleReportsOnlyDocumentedHeuristicSignals() {
        PolicyBundle bundle = distillBundle();
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
        DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

        assertEquals(1, runtime.execute(bundle.matchers().get("operation-semantics"), api, bundle.rules().get("HTTP001")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("operation-semantics"), api, bundle.rules().get("HTTP002")).size());
        assertEquals(1, runtime.execute(bundle.matchers().get("operation-semantics"), api, bundle.rules().get("HTTP003")).size());
        assertEquals(2, runtime.execute(bundle.matchers().get("operation-semantics"), api, bundle.rules().get("HTTP006")).size());
        assertTrue(runtime.execute(bundle.matchers().get("operation-semantics"), api, bundle.rules().get("HTTP008")).isEmpty());
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
        assertResource("api-policy/policies/EnterpriseGrade.md");
        assertResource("api-policy/matchers/resource-path/Matcher.md");
        assertResource("api-policy/matchers/operation/Matcher.md");
        assertResource("api-policy/matchers/text-style/Matcher.md");
        assertResource("api-policy/matchers/proprietary-header/Matcher.md");
        assertResource("api-policy/matchers/query-collection/Matcher.md");
        assertResource("api-policy/matchers/security/Matcher.md");
        assertResource("api-policy/matchers/openapi-version/Matcher.md");
        assertResource("api-policy/matchers/media-type/Matcher.md");
        assertResource("api-policy/matchers/error-response/Matcher.md");
    }

    private static SpecInput input(String content) {
        return input(content, "Enterprise Grade");
    }

    private static SpecInput input(String content, String ruleSet) {
        return SpecInput.builder().content(content).format(SpecFormat.OPENAPI3).ruleSet(ruleSet).build();
    }

    private static Map<String, String> bundledResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : new String[] {"PolicyBundle.yaml", "rules/REST001.md", "rules/REST002.md", "rules/REST003.md", "rules/REST004.md", "rules/REST005.md", "rules/REST006.md", "rules/HTTP001.md", "rules/HTTP002.md", "rules/HTTP003.md", "rules/HTTP004.md", "rules/HTTP005.md", "rules/HTTP006.md", "rules/HTTP008.md", "rules/UPDATE001.md", "rules/UPDATE002.md", "rules/UPDATE003.md", "rules/BULK001.md", "rules/BULK002.md", "rules/BULK003.md", "rules/VERSION001.md", "rules/VERSION002.md", "rules/VERSION003.md", "rules/VERSION004.md", "rules/COMPAT001.md", "rules/COMPAT002.md", "rules/COMPAT003.md", "rules/COMPAT004.md", "rules/COMPAT005.md", "rules/COMPAT006.md", "rules/STATUS001.md", "rules/STATUS002.md", "rules/STATUS003.md", "rules/STATUS004.md", "rules/STATUS005.md", "rules/STATUS006.md", "rules/STATUS007.md", "rules/DOC001.md", "rules/DOC002.md", "rules/DOC003.md", "rules/DOC004.md", "rules/DOC005.md", "rules/DOC006.md", "rules/DOC009.md", "rules/CASE001.md", "rules/CASE002.md", "rules/CASE003.md", "rules/CASE004.md", "rules/CASE005.md", "rules/JSON003.md", "rules/JSON004.md", "rules/JSON006.md", "rules/JSON007.md", "rules/JSON009.md", "rules/STANDARD008.md", "rules/STANDARD009.md", "rules/SECURITY001.md", "rules/SECURITY002.md", "rules/ERROR001.md", "rules/ERROR002.md", "rules/ERROR003.md", "rules/ERROR004.md", "rules/ERROR005.md", "rules/ERROR006.md", "rules/ERROR007.md", "policies/EnterpriseGrade.md", "matchers/resource-path/Matcher.md", "matchers/resource-path/Matcher.groovy", "matchers/operation/Matcher.md", "matchers/operation/Matcher.groovy", "matchers/text-style/Matcher.md", "matchers/text-style/Matcher.groovy", "matchers/naming/Matcher.md", "matchers/naming/Matcher.groovy", "matchers/schema/Matcher.md", "matchers/schema/Matcher.groovy", "matchers/operation-semantics/Matcher.md", "matchers/operation-semantics/Matcher.groovy", "matchers/response-code/Matcher.md", "matchers/response-code/Matcher.groovy", "matchers/response-header/Matcher.md", "matchers/response-header/Matcher.groovy", "matchers/proprietary-header/Matcher.md", "matchers/proprietary-header/Matcher.groovy", "matchers/query-collection/Matcher.md", "matchers/query-collection/Matcher.groovy", "matchers/security/Matcher.md", "matchers/security/Matcher.groovy", "matchers/manual/Matcher.md", "matchers/manual/Matcher.groovy", "matchers/bulk-operation/Matcher.md", "matchers/bulk-operation/Matcher.groovy", "matchers/versioning/Matcher.md", "matchers/versioning/Matcher.groovy", "matchers/compatibility/Matcher.md", "matchers/compatibility/Matcher.groovy", "matchers/metadata/Matcher.md", "matchers/metadata/Matcher.groovy", "matchers/error-response/Matcher.md", "matchers/error-response/Matcher.groovy"}) {
            if (path.endsWith("Matcher.groovy")) continue;
            resources.put(path, readResource("api-policy/" + path));
            if (path.endsWith("Matcher.md")) {
                String dslPath = path.replace("Matcher.md", "Matcher.dsl");
                resources.put(dslPath, readResource("api-policy/" + dslPath));
            }
        }
        resources.put("rules/STANDARD010.md", readResource("api-policy/rules/STANDARD010.md"));
        resources.put("matchers/openapi-version/Matcher.md", readResource("api-policy/matchers/openapi-version/Matcher.md"));
        resources.put("rules/CONTENT001.md", readResource("api-policy/rules/CONTENT001.md"));
        resources.put("rules/CONTENT002.md", readResource("api-policy/rules/CONTENT002.md"));
        resources.put("rules/CONTENT003.md", readResource("api-policy/rules/CONTENT003.md"));
        resources.put("rules/CONTENT004.md", readResource("api-policy/rules/CONTENT004.md"));
        resources.put("matchers/media-type/Matcher.md", readResource("api-policy/matchers/media-type/Matcher.md"));
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
        resources.put("matchers/date-time-name/Matcher.md", readResource("api-policy/matchers/date-time-name/Matcher.md"));
        resources.put("matchers/common-field/Matcher.md", readResource("api-policy/matchers/common-field/Matcher.md"));
        resources.put("matchers/path-count/Matcher.md", readResource("api-policy/matchers/path-count/Matcher.md"));
        resources.put("policies/Zalando.md", readResource("api-policy/policies/Zalando.md"));
        resources.put("policies/ZalandoExtended.md", readResource("api-policy/policies/ZalandoExtended.md"));
        for (String matcher : new String[] {"path-prefix", "tag", "component-usage",
                "array-items", "security-scheme", "path-syntax", "markdown-safety"}) {
            resources.put("matchers/" + matcher + "/Matcher.md", readResource("api-policy/matchers/" + matcher + "/Matcher.md"));
            resources.put("matchers/" + matcher + "/Matcher.dsl", readResource("api-policy/matchers/" + matcher + "/Matcher.dsl"));
        }
        for (String rule : new String[] {"JSON021", "JSON022", "JSON023", "JSON024", "REST007", "CASE008",
                "DOC017", "DOC018", "DOC019", "DOC020", "STANDARD024", "STANDARD025", "STANDARD026",
                "STANDARD027", "STANDARD028", "SECURITY003", "SECURITY004"}) {
            resources.put("rules/" + rule + ".md", readResource("api-policy/rules/" + rule + ".md"));
        }
        resources.put("matchers/hostname/Matcher.md", readResource("api-policy/matchers/hostname/Matcher.md"));
        resources.put("rules/ERROR008.md", readResource("api-policy/rules/ERROR008.md"));
        resources.put("rules/ERROR009.md", readResource("api-policy/rules/ERROR009.md"));
        resources.put("rules/ERROR010.md", readResource("api-policy/rules/ERROR010.md"));
        resources.put("matchers/authentication-error/Matcher.md", readResource("api-policy/matchers/authentication-error/Matcher.md"));
        resources.put("rules/SEC001.md", readResource("api-policy/rules/SEC001.md"));
        resources.put("rules/SEC002.md", readResource("api-policy/rules/SEC002.md"));
        resources.put("rules/SEC003.md", readResource("api-policy/rules/SEC003.md"));
        resources.put("rules/SEC008.md", readResource("api-policy/rules/SEC008.md"));
        resources.put("matchers/sensitive-data/Matcher.md", readResource("api-policy/matchers/sensitive-data/Matcher.md"));
        resources.put("rules/SEC004.md", readResource("api-policy/rules/SEC004.md"));
        resources.put("rules/SEC005.md", readResource("api-policy/rules/SEC005.md"));
        resources.put("matchers/sensitive-search/Matcher.md", readResource("api-policy/matchers/sensitive-search/Matcher.md"));
        resources.put("rules/SEC006.md", readResource("api-policy/rules/SEC006.md"));
        resources.put("rules/SEC007.md", readResource("api-policy/rules/SEC007.md"));
        resources.put("matchers/identifier/Matcher.md", readResource("api-policy/matchers/identifier/Matcher.md"));
        resources.put("rules/FILTER001.md", readResource("api-policy/rules/FILTER001.md"));
        resources.put("rules/FILTER002.md", readResource("api-policy/rules/FILTER002.md"));
        resources.put("rules/SORT001.md", readResource("api-policy/rules/SORT001.md"));
        resources.put("rules/SORT002.md", readResource("api-policy/rules/SORT002.md"));
        resources.put("rules/SORT003.md", readResource("api-policy/rules/SORT003.md"));
        resources.put("rules/SORT004.md", readResource("api-policy/rules/SORT004.md"));
        resources.put("rules/FIELD001.md", readResource("api-policy/rules/FIELD001.md"));
        resources.put("matchers/collection-capability/Matcher.md", readResource("api-policy/matchers/collection-capability/Matcher.md"));
        resources.put("rules/PAGE001.md", readResource("api-policy/rules/PAGE001.md"));
        resources.put("rules/PAGE002.md", readResource("api-policy/rules/PAGE002.md"));
        resources.put("rules/PAGE003.md", readResource("api-policy/rules/PAGE003.md"));
        resources.put("rules/PAGE004.md", readResource("api-policy/rules/PAGE004.md"));
        resources.put("rules/PAGE005.md", readResource("api-policy/rules/PAGE005.md"));
        resources.put("rules/PAGE006.md", readResource("api-policy/rules/PAGE006.md"));
        resources.put("matchers/pagination/Matcher.md", readResource("api-policy/matchers/pagination/Matcher.md"));
        for (String matcherId : new String[] {"STANDARD011", "STANDARD012", "STANDARD013", "STANDARD014", "STANDARD015",
                "STANDARD016", "STANDARD017", "STANDARD018", "STANDARD019", "STANDARD020", "STANDARD021", "STANDARD022",
                "HTTP009", "DOC010", "DOC011", "DOC012", "DOC013", "DOC014", "CASE006", "CASE007", "JSON017", "JSON018", "JSON019", "JSON020", "DOC015", "DOC016", "ERROR011", "SEC009", "STANDARD023",
                "STATUS008"}) {
            resources.put("rules/" + matcherId + ".md", readResource("api-policy/rules/" + matcherId + ".md"));
        }
        for (String matcherId : new String[] {"resource-path", "operation", "text-style", "naming", "schema",
                "operation-semantics", "response-code", "response-header", "proprietary-header", "query-collection",
                "security", "manual", "bulk-operation", "versioning", "compatibility", "metadata", "openapi-version",
                "media-type", "date-time-name", "common-field", "path-count", "hostname", "error-response", "authentication-error", "sensitive-data", "sensitive-search", "identifier", "collection-capability", "pagination",
                "parameter", "request-body", "operation-metadata", "api-title", "schema-name", "path-set", "header-schema", "enum-values", "server-url",
                "extensions", "documentation-completeness", "schema-composition", "document-lint", "status-class", "example-validity", "response-example"}) {
            resources.put("matchers/" + matcherId + "/Matcher.md",
                    readResource("api-policy/matchers/" + matcherId + "/Matcher.md"));
            resources.put("matchers/" + matcherId + "/Matcher.dsl",
                    readResource("api-policy/matchers/" + matcherId + "/Matcher.dsl"));
        }
        return resources;
    }

    private static void assertResource(String resource) {
        try (InputStream stream = PolicyBasedValidationPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be packaged");
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }

    private static String readResource(String resource) {
        try (InputStream stream = PolicyBasedValidationPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }

    @Test
    void loadsUserPoliciesFromAConfiguredDirectoryAndOverridesBundledOnes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("Lenient.md"), """
                ---
                id: Lenient
                rules:
                  REST001: 0.1
                ---

                # Lenient Policy

                A light-touch profile.
                """);
        // Same id as a bundled policy: the user file must win.
        java.nio.file.Files.writeString(dir.resolve("Zzz-EnterpriseGrade.md"), """
                ---
                id: Enterprise Grade
                rules:
                  REST001: PROHIBITED
                ---

                # Overridden

                Replaces the bundled Enterprise Grade.
                """);

        PolicyBasedValidationPlugin plugin = new PolicyBasedValidationPlugin();
        plugin.configure(Map.of("policies-dir", dir.toString()));

        assertTrue(plugin.getRuleSets().contains("Lenient"));

        ValidationResult lenient = plugin.validate(input(ACTION_PATH_SPEC, "Lenient"));
        ValidationResult enterprise = plugin.validate(input(ACTION_PATH_SPEC, "Enterprise Grade"));
        // Lenient enables only REST001 and deducts 0.1 once for it.
        assertEquals(99.9, lenient.getOverallScore(), 1e-9);
        // The overridden Enterprise Grade prohibits REST001, so it blocks.
        assertEquals(0.0, enterprise.getOverallScore());
    }

    @Test
    void ignoresAMissingUserPolicyDirectory() {
        PolicyBasedValidationPlugin plugin = new PolicyBasedValidationPlugin();
        plugin.configure(Map.of("policies-dir", "/no/such/arete/policies"));
        assertTrue(plugin.getRuleSets().contains("Enterprise Grade"));
    }

    /** Bundle pinned to the Distill runtime for tests that drive it directly. */
    private static PolicyBundle distillBundle() {
        return new PolicyBundleLoader().load(
                new ClasspathBundleResources(PolicyBasedValidationPluginTest.class.getClassLoader()),
                new PolicyBundleLoader.LoadOptions(java.util.List.of("distill")));
    }
}
