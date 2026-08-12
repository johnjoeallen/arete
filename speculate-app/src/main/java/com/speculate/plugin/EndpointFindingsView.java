package com.speculate.plugin;

import java.util.List;

/** One endpoint's validation findings: badge counts for the collapsed header, full list for the expanded body. */
public record EndpointFindingsView(SeverityCounts counts, List<AttributedViolation> violations) {
}
