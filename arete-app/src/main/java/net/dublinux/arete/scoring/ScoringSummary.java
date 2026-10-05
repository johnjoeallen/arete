package net.dublinux.arete.scoring;

/** View-model row for one engine's {@code score()} outcome, for rendering in the UI. */
public record ScoringSummary(String engineName, String status, int diagnosticCount, String errorMessage) {
}
