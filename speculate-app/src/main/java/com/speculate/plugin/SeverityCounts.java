package com.speculate.plugin;

import net.dublinux.speculate.validation.spi.Severity;

import java.util.Map;

/** Per-severity finding counts for one endpoint, for the UI's badge row. */
public record SeverityCounts(long errorCount, long warningCount, long infoCount, long hintCount) {

    static SeverityCounts of(Map<Severity, Long> bySeverity) {
        return new SeverityCounts(
                bySeverity.getOrDefault(Severity.ERROR, 0L),
                bySeverity.getOrDefault(Severity.WARNING, 0L),
                bySeverity.getOrDefault(Severity.INFO, 0L),
                bySeverity.getOrDefault(Severity.HINT, 0L));
    }

    public long total() {
        return errorCount + warningCount + infoCount + hintCount;
    }
}
