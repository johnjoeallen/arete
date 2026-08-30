package net.dublinux.arete.plugin;

/** JSON-serializable mirror of {@link ValidationSummary}. */
public record PersistedValidationSummary(String pluginName, String status, int diagnosticCount, String errorMessage) {
}
