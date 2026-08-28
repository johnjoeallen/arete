package com.speculate.validation.policy;

import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenericPolicyValidationPluginTest {

    @Test
    void exposesTheBundledStarterPolicyAndReturnsAnEmptySuccessfulResult() {
        GenericPolicyValidationPlugin plugin = new GenericPolicyValidationPlugin();

        assertEquals("generic-policy", plugin.getId());
        assertEquals(1, plugin.getRuleSets().size());
        assertEquals(GenericPolicyValidationPlugin.STARTER_POLICY, plugin.getRuleSets().get(0));

        ValidationResult result = plugin.validate(SpecInput.builder()
                .content("openapi: 3.0.0\ninfo:\n  title: Test\n  version: 1\npaths: {}\n")
                .format(SpecFormat.OPENAPI3)
                .ruleSet(GenericPolicyValidationPlugin.STARTER_POLICY)
                .build());

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(0, result.getRulesEvaluatedCount());
        assertEquals(0, result.getViolations().size());
    }

    @Test
    void packagesTheStarterBundleResources() {
        assertResource("api-policy/PolicyBundle.json");
        assertResource("api-policy/Rules.md");
        assertResource("api-policy/policies/Starter.md");
    }

    private static void assertResource(String resource) {
        try (InputStream stream = GenericPolicyValidationPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " must be packaged");
        } catch (Exception e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }
}
