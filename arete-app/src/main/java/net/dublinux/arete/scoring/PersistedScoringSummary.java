package net.dublinux.arete.scoring;

/** JSON-serializable mirror of {@link ScoringSummary}. */
public record PersistedScoringSummary(String engineName, String status, int diagnosticCount, String errorMessage) {
}
