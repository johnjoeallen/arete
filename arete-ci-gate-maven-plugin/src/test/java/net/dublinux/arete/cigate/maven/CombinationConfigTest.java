package net.dublinux.arete.cigate.maven;

import net.dublinux.arete.cigate.Combination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinationConfigTest {

    @Test
    void mapsRunAndOptionalOntoACombination() {
        CombinationConfig config = new CombinationConfig();
        config.setRun("generic-policy/Enterprise Grade");
        config.setOptional(true);

        Combination c = config.toCombination();

        assertEquals("generic-policy", c.validator());
        assertEquals("Enterprise Grade", c.policy());
        assertTrue(c.optional());
    }

    @Test
    void defaultsToGating() {
        CombinationConfig config = new CombinationConfig();
        config.setRun("generic-policy/Zalando");

        assertFalse(config.toCombination().optional());
    }
}
