package net.dublinux.arete.plugin;

import net.dublinux.arete.scoring.spi.Diagnostic;

/**
 * A {@link Diagnostic} tagged with the plugin that produced it.
 * {@code Diagnostic} itself carries no plugin identity, so the aggregation
 * step wraps every diagnostic from every plugin's result in one of these
 * before combining them into a single list, otherwise the UI would have no
 * way to show which validator found what.
 */
public record AttributedDiagnostic(String pluginId, String pluginName, Diagnostic diagnostic) {
}
