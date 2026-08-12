package com.speculate.plugin;

/** View-model row for one plugin's {@code validate()} outcome, for rendering in the UI. */
public record ValidationSummary(String pluginName, String status, int violationCount, String errorMessage) {
}
