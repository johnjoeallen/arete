package net.dublinux.arete.validation.policy;

import net.dublinux.arete.validation.spi.SpecFormat;
import net.dublinux.arete.validation.spi.SpecInput;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import net.dublinux.arete.validation.spi.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the shaded bundle loads exactly like a jar placed in plugins/. */
class GenericPolicyValidationPluginLoadIT {
    @Test
    void discoversAndExecutesThroughAnIsolatedClassLoader() throws Exception {
        File jar = findBuiltJar();
        URLClassLoader isolated = new URLClassLoader(new URL[] {jar.toURI().toURL()}, SpecValidationPlugin.class.getClassLoader());
        ServiceLoader<SpecValidationPlugin> loader = ServiceLoader.load(SpecValidationPlugin.class, isolated);
        Iterator<SpecValidationPlugin> plugins = loader.iterator();
        assertTrue(plugins.hasNext(), "expected generic policy plugin discovery through ServiceLoader");

        SpecValidationPlugin plugin = plugins.next();
        assertEquals("generic-policy", plugin.getId());
        plugin.configure(Map.of());
        ValidationResult result = plugin.validate(SpecInput.builder()
                .content("""
                        openapi: 3.0.0
                        info: { title: Test, version: 1 }
                        paths:
                          /getCustomers:
                            get:
                              responses:
                                '200': { description: OK }
                        """)
                .format(SpecFormat.OPENAPI3)
                .ruleSet("Enterprise Grade")
                .build());

        assertEquals(ValidationResult.Status.SUCCESS, result.getStatus());
        assertEquals(18, result.getDiagnostics().size());
        assertEquals(93.0, result.getOverallScore());
    }

    private static File findBuiltJar() {
        File[] jars = new File("target").listFiles((directory, name) ->
                name.startsWith("generic-policy-validation-plugin-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) throw new IllegalStateException("No packaged generic-policy plugin jar found");
        return jars[0];
    }
}
