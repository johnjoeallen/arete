package net.dublinux.arete.scoring;

import net.dublinux.arete.engine.api.Diagnostic;

/**
 * A {@link Diagnostic} tagged with the engine that produced it.
 * {@code Diagnostic} itself carries no engine identity, so the aggregation
 * step wraps every diagnostic from every engine's result in one of these
 * before combining them into a single list, otherwise the UI would have no
 * way to show which validator found what.
 */
public record AttributedDiagnostic(String engineId, String engineName, Diagnostic diagnostic) {
}
