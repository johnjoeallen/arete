package net.dublinux.arete.plugin;

import java.util.List;

/** One endpoint's scoring findings: badge counts for the collapsed header, full list for the expanded body. */
public record EndpointFindingsView(SeverityCounts counts, List<AttributedDiagnostic> diagnostics) {
}
