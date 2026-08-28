package com.speculate.validation.policy;

import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final String QUERY_PREDICATE_SPEC = """
            openapi: 3.0.0
            info:
              title: Test API
              version: 1.0.0
            paths:
              /pet/findByStatus:
                get:
                  responses:
                    '200': { description: OK }
            """;

    @Test
    void executesTheBundledGroovyDetectorAndDeductsOnlyOncePerRule() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(ACTION_PATH_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(2, result.getRulesEvaluatedCount());
        assertEquals(2, result.getViolations().size());
        assertEquals("REST001", result.getViolations().get(0).getRuleId());
        assertEquals(90, result.getOverallScore());
        assertEquals(90, result.getOverallScoreWithoutBlockers());
        assertEquals(10, result.getViolations().get(0).getScoreImprovement());
        assertEquals(10, result.getViolations().get(1).getScoreImprovement());
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
    void reportsQueryPredicatePathsAgainstTheirAffectedOperation() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();
        plugin.configure(Map.of());

        ValidationResult result = plugin.validate(input(QUERY_PREDICATE_SPEC));

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(1, result.getViolations().size());
        assertEquals("REST002", result.getViolations().get(0).getRuleId());
        assertEquals("/paths/~1pet~1findByStatus/get", result.getViolations().get(0).getPointer());
        assertEquals(java.util.List.of("GET /pet/findByStatus"), result.getViolations().get(0).getPaths());
        assertEquals(95, result.getOverallScore());
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
    void packagesTheStarterBundleResources() {
        assertResource("api-policy/PolicyBundle.yaml");
        assertResource("api-policy/rules/REST001.md");
        assertResource("api-policy/policies/Starter.md");
        assertResource("api-policy/detectors/resource-path/Detector.md");
        assertResource("api-policy/detectors/resource-path/Detector.groovy");
    }

    private static SpecInput input(String content) {
        return SpecInput.builder().content(content).format(SpecFormat.OPENAPI3).ruleSet("Starter").build();
    }

    private static Map<String, String> bundledResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String path : new String[] {"PolicyBundle.yaml", "rules/REST001.md", "rules/REST002.md", "policies/Starter.md", "detectors/resource-path/Detector.md", "detectors/resource-path/Detector.groovy"}) {
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
