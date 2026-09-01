package net.dublinux.arete.scoring.spi;

import java.util.Optional;

/**
 * Optional capability for a plugin that can supply rendered-by-host
 * documentation for its rule IDs. Plugins that do not implement this remain
 * fully compatible with the scoring SPI.
 */
public interface RuleDocumentationProvider {

    /** Documentation for {@code ruleId}, or empty when the rule is unknown or unpublished. */
    Optional<RuleDocumentation> getRuleDocumentation(String ruleId);
}
