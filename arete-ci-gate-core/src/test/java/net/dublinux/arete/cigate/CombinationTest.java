package net.dublinux.arete.cigate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinationTest {

    @Test
    void parsesValidatorAndPolicy() {
        Combination c = Combination.parse("generic-policy/Enterprise Grade", false);
        assertEquals("generic-policy", c.validator());
        assertEquals("Enterprise Grade", c.policy());
        assertEquals("generic-policy/Enterprise Grade", c.run());
        assertFalse(c.optional());
    }

    @Test
    void keepsSlashesInsideThePolicyName() {
        Combination c = Combination.parse("v/a/b", true);
        assertEquals("v", c.validator());
        assertEquals("a/b", c.policy());
        assertTrue(c.optional());
    }

    @Test
    void rejectsMissingParts() {
        assertThrows(IllegalArgumentException.class, () -> Combination.parse("no-slash", false));
        assertThrows(IllegalArgumentException.class, () -> Combination.parse("/policy", false));
        assertThrows(IllegalArgumentException.class, () -> Combination.parse("validator/", false));
    }
}
