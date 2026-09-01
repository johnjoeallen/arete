package net.dublinux.arete.plugin;

import net.dublinux.arete.scoring.spi.Severity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Per-severity finding counts for one endpoint or schema, for the UI's badge row. */
public record SeverityCounts(long errorCount, long warningCount, long infoCount, long hintCount) {

    static SeverityCounts of(Map<Severity, Long> bySeverity) {
        return new SeverityCounts(
                bySeverity.getOrDefault(Severity.ERROR, 0L),
                bySeverity.getOrDefault(Severity.WARNING, 0L),
                bySeverity.getOrDefault(Severity.INFO, 0L),
                bySeverity.getOrDefault(Severity.HINT, 0L));
    }

    /** Tallies a group of findings (e.g. everything under one endpoint or one schema) by severity. */
    static SeverityCounts from(List<AttributedDiagnostic> diagnostics) {
        EnumMap<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (AttributedDiagnostic av : diagnostics) {
            bySeverity.merge(av.diagnostic().getSeverity(), 1L, Long::sum);
        }
        return of(bySeverity);
    }

    public long total() {
        return errorCount + warningCount + infoCount + hintCount;
    }
}
