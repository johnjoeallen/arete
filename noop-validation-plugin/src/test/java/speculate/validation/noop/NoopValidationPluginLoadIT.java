package speculate.validation.noop;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the packaged {@code noop-validation-plugin} jar through an isolated
 * {@code URLClassLoader}, whose parent is scoped to only the SPI classes —
 * mirroring how the host's plugin loader will load any plugin jar dropped in
 * {@code plugins/}. Runs as a Failsafe integration test (bound to
 * integration-test/verify) so it executes after the jar has actually been
 * packaged.
 */
class NoopValidationPluginLoadIT {

    @Test
    void discoversAndValidatesThroughIsolatedClassLoader() throws Exception {
        File jar = findBuiltJar();

        URLClassLoader isolated = new URLClassLoader(
                new URL[] {jar.toURI().toURL()},
                SpecValidationPlugin.class.getClassLoader());

        ServiceLoader<SpecValidationPlugin> loader = ServiceLoader.load(SpecValidationPlugin.class, isolated);
        Iterator<SpecValidationPlugin> it = loader.iterator();
        assertTrue(it.hasNext(), "expected the noop plugin to be discovered via ServiceLoader");

        SpecValidationPlugin plugin = it.next();
        assertEquals("noop", plugin.getId());

        plugin.configure(Map.of());

        SpecInput input = SpecInput.builder()
                .content("irrelevant: true")
                .format(SpecFormat.OPENAPI3)
                .build();

        ValidationResult result = plugin.validate(input);

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.getViolations().isEmpty());
    }

    private static File findBuiltJar() {
        File targetDir = new File("target");
        File[] jars = targetDir.listFiles((dir, name) ->
                name.startsWith("noop-validation-plugin-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException(
                    "No built jar found in target/ — this test must run after the package phase");
        }
        return jars[0];
    }
}
