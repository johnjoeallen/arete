package com.speculate.plugin;

import speculate.validation.spi.Violation;

/**
 * A {@link Violation} tagged with the plugin that produced it.
 * {@code Violation} itself carries no plugin identity, so the aggregation
 * step wraps every violation from every plugin's result in one of these
 * before combining them into a single list, otherwise the UI would have no
 * way to show which validator found what.
 */
public record AttributedViolation(String pluginId, String pluginName, Violation violation) {
}
