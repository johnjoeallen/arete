package net.dublinux.arete.plugin;

/** View-model row for one plugin's {@code score()} outcome, for rendering in the UI. */
public record ScoringSummary(String pluginName, String status, int diagnosticCount, String errorMessage) {
}
