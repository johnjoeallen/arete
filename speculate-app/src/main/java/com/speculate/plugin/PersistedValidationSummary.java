package com.speculate.plugin;

/** JSON-serializable mirror of {@link ValidationSummary}. */
public record PersistedValidationSummary(String pluginName, String status, int violationCount, String errorMessage) {
}
