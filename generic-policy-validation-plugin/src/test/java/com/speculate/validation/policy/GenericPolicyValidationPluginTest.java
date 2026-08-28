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

    @Test
    void executesTheBundledGroovyDetectorAndDeductsOnlyOncePerRule() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(1, result.getRulesEvaluatedCount());
        assertEquals(2, result.getViolations().size());
        assertEquals("REST001", result.getViolations().get(0).getRuleId());
        assertEquals(90, result.getOverallScore());
        assertEquals(90, result.getOverallScoreWithoutBlockers());
        assertEquals(10, result.getViolations().get(0).getScoreImprovement());
        assertEquals(10, result.getViolations().get(1).getScoreImprovement());
        assertEquals("http://localhost:6809/plugins/generic-policy/rules/REST001",
                result.getViolations().get(0).getDocumentationUrl());
        assertTrue(plugin.getRuleDocumentation("REST001").orElseThrow().markdown().contains("GET /customers"));
    }

    @Test
    void returnsAFullScoreWhenTheRuleDoesNotMatch() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC.replace("/getAllCustomers", "/customers-list").replace("/deleteCustomer", "/customer")));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getViolations().isEmpty());
        assertEquals(100, result.getOverallScore());
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
    void packagesTheStarterBundleResources() {
        assertResource("api-policy/PolicyBundle.yaml");
        assertResource("api-policy/rules/REST001.md");
        assertResource("api-policy/rules/DOC001.md");
        assertResource("api-policy/rules/DOC002.md");
        assertResource("api-policy/rules/DOC003.md");
        assertResource("api-policy/rules/DOC004.md");
        assertResource("api-policy/rules/DOC005.md");
        assertResource("api-policy/rules/DOC009.md");
        assertResource("api-policy/policies/Starter.md");
        assertResource("api-policy/detectors/resource-path/Detector.md");
        assertResource("api-policy/detectors/resource-path/Detector.groovy");
        assertResource("api-policy/detectors/operation/Detector.md");
        assertResource("api-policy/detectors/operation/Detector.groovy");
        assertResource("api-policy/detectors/text-style/Detector.md");
        assertResource("api-policy/detectors/text-style/Detector.groovy");
    }

    private static SpecInput input(String content) {
        return SpecInput.builder().content(content).format(SpecFormat.OPENAPI3).ruleSet("Starter").build();
    }

    private static Map<String, String> bundledResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : new String[] {"PolicyBundle.yaml", "rules/REST001.md", "rules/DOC001.md", "rules/DOC002.md", "rules/DOC003.md", "rules/DOC004.md", "rules/DOC005.md", "rules/DOC009.md", "policies/Starter.md", "detectors/resource-path/Detector.md", "detectors/resource-path/Detector.groovy", "detectors/operation/Detector.md", "detectors/operation/Detector.groovy", "detectors/text-style/Detector.md", "detectors/text-style/Detector.groovy"}) {
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
