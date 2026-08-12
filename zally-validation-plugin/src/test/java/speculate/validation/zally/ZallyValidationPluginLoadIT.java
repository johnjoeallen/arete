package speculate.validation.zally;

import org.junit.jupiter.api.Test;
import speculate.validation.spi.SpecFormat;
import speculate.validation.spi.SpecInput;
import speculate.validation.spi.SpecValidationPlugin;
import speculate.validation.spi.ValidationResult;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the packaged {@code zally-validation-plugin} jar through an isolated
 * {@code URLClassLoader}, whose parent is scoped to only the SPI classes —
 * mirroring how the host's plugin loader will load any plugin jar dropped in
 * {@code plugins/}. Runs as a Failsafe integration test (bound to
 * integration-test/verify) so it executes after the jar has actually been
 * shaded, which is what proves the shading actually bundled Zally's rule
 * classes and {@code META-INF/services} entries correctly — the exact thing
 * that would silently produce zero discovered rules if the shade config were
 * wrong.
 */
class ZallyValidationPluginLoadIT {

    // Deliberately missing the Zalando ruleset's x-audience extension, so
    // there's at least one guaranteed violation to assert on regardless of
    // how the rest of the ruleset evolves.
    private static final String SPEC_MISSING_AUDIENCE = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /items:
                get:
                  operationId: listItems
                  responses:
                    '200':
                      description: OK
            """;

    @Test
    void discoversAndValidatesThroughIsolatedClassLoader() throws Exception {
        File jar = findBuiltJar();

        URLClassLoader isolated = new URLClassLoader(
                new URL[] {jar.toURI().toURL()},
                SpecValidationPlugin.class.getClassLoader());

        ServiceLoader<SpecValidationPlugin> loader = ServiceLoader.load(SpecValidationPlugin.class, isolated);
        Iterator<SpecValidationPlugin> it = loader.iterator();
        assertTrue(it.hasNext(), "expected the zally plugin to be discovered via ServiceLoader");

        SpecValidationPlugin plugin = it.next();
        assertEquals("zally-core", plugin.getId());

        plugin.configure(Map.of());

        SpecInput input = SpecInput.builder()
                .content(SPEC_MISSING_AUDIENCE)
                .format(SpecFormat.OPENAPI3)
                .build();

        ValidationResult result = plugin.validate(input);

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getRulesEvaluatedCount() > 0, "expected the shaded jar to have discovered Zally's rules");
        assertFalse(result.getViolations().isEmpty(),
                "expected at least one violation for a spec missing the x-audience extension");
    }

    private static File findBuiltJar() {
        File targetDir = new File("target");
        File[] jars = targetDir.listFiles((dir, name) ->
                name.startsWith("zally-validation-plugin-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException(
                    "No built jar found in target/ — this test must run after the package phase");
        }
        return jars[0];
    }
}
