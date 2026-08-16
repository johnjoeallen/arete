package com.speculate.plugin;

/** JSON-serializable mirror of {@link AttributedViolation}. */
public record PersistedAttributedViolation(String pluginId, String pluginName, PersistedViolation violation) {
}
