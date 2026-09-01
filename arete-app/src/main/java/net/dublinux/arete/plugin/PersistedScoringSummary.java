package net.dublinux.arete.plugin;

/** JSON-serializable mirror of {@link ScoringSummary}. */
public record PersistedScoringSummary(String pluginName, String status, int diagnosticCount, String errorMessage) {
}
