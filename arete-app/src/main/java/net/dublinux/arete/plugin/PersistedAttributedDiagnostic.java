package net.dublinux.arete.plugin;

/** JSON-serializable mirror of {@link AttributedDiagnostic}. */
public record PersistedAttributedDiagnostic(String pluginId, String pluginName, PersistedDiagnostic diagnostic) {
}
