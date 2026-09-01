package net.dublinux.arete.scoring.policy;

import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecInput;
import net.dublinux.arete.scoring.spi.SpecScoringPlugin;
import net.dublinux.arete.scoring.spi.ScoringResult;
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
class PolicyScoringPluginLoadIT {
    @Test
    void discoversAndExecutesThroughAnIsolatedClassLoader() throws Exception {
        File jar = findBuiltJar();
        URLClassLoader isolated = new URLClassLoader(new URL[] {jar.toURI().toURL()}, SpecScoringPlugin.class.getClassLoader());
        ServiceLoader<SpecScoringPlugin> loader = ServiceLoader.load(SpecScoringPlugin.class, isolated);
        Iterator<SpecScoringPlugin> plugins = loader.iterator();
        assertTrue(plugins.hasNext(), "expected generic policy plugin discovery through ServiceLoader");

        SpecScoringPlugin plugin = plugins.next();
        assertEquals("generic-policy", plugin.getId());
        plugin.configure(Map.of());
        ScoringResult result = plugin.score(SpecInput.builder()
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

        assertEquals(ScoringResult.Status.SUCCESS, result.getStatus());
        assertEquals(19, result.getDiagnostics().size());
        assertEquals(92.5, result.getOverallScore());
    }

    private static File findBuiltJar() {
        File[] jars = new File("target").listFiles((directory, name) ->
                name.startsWith("arete-policy-plugin-") && name.endsWith(".jar"));
        if (jars == null || jars.length == 0) throw new IllegalStateException("No packaged generic-policy plugin jar found");
        return jars[0];
    }
}
