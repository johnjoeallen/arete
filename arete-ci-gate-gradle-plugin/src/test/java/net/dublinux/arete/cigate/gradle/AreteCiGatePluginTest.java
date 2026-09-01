package net.dublinux.arete.cigate.gradle;

import net.dublinux.arete.cigate.Combination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level checks only. Applying the plugin to a real project (extension +
 * task wiring, {@code check.dependsOn}) needs a Gradle runtime and is covered
 * by the TestKit integration job in CI, not here — the reactor only has the
 * API-only {@code dev.gradleplugins:gradle-api} on the test classpath.
 */
class AreteCiGatePluginTest {

    @Test
    void combinationSpecMapsOntoACombination() {
        CombinationSpec spec = new CombinationSpec("generic-policy/Enterprise Grade");
        spec.setOptional(true);

        Combination c = spec.toCombination();

        assertEquals("generic-policy", c.validator());
        assertEquals("Enterprise Grade", c.policy());
        assertTrue(c.optional());
    }

    @Test
    void combinationSpecDefaultsToGating() {
        assertFalse(new CombinationSpec("generic-policy/Zalando").toCombination().optional());
    }
}
