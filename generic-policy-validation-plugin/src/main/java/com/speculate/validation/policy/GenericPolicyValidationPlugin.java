package com.speculate.validation.policy;

import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
import net.dublinux.speculate.validation.spi.ValidationResult;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The host-side entry point for the generic policy engine.
 *
 * <p>This initial implementation intentionally proves only the plugin and
 * bundle-delivery contract: it is discoverable through the existing SPI,
 * declares the bundled starter policy as its single rule set, and performs no
 * checks yet. Rule parsing, detector execution, and scoring will be added as
 * the policy-engine stages are implemented.</p>
 */
public final class GenericPolicyValidationPlugin implements SpecValidationPlugin {

    public static final String STARTER_POLICY = "Starter";

    @Override
    public String getId() {
        return "generic-policy";
    }

    @Override
    public String getName() {
        return "Generic API Policy (starter)";
    }

    @Override
    public String getVersion() {
        return "0.1.0-SNAPSHOT";
    }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    @Override
    public List<String> getRuleSets() {
        return List.of(STARTER_POLICY);
    }

    @Override
    public void configure(Map<String, String> config) {
        // No configuration is consumed until PolicySource is implemented.
    }

    @Override
    public ValidationResult validate(SpecInput input) {
        // Do not claim that the starter rule was evaluated until detectors and
        // the rule parser exist. A successful empty result accurately expresses
        // the current dummy plugin behaviour.
        return ValidationResult.success(List.of(), 0);
    }
}
