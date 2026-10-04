package net.dublinux.arete.scoring;

/** JSON-serializable mirror of {@link AttributedDiagnostic}. */
public record PersistedAttributedDiagnostic(String engineId, String engineName, PersistedDiagnostic diagnostic) {
}
