package net.dublinux.arete.plugin;

import java.util.List;

/**
 * JSON-serializable mirror of {@link net.dublinux.arete.scoring.spi.Diagnostic},
 * for storing a scoring run's findings in {@link SpecScoringResultEntity}.
 * A plain DTO rather than annotating the SPI class itself, so the SPI stays
 * free of any host-persistence concern. {@code scoreImprovement} is {@code
 * null} for {@link Double#NaN} ("not computed") — see {@link
 * ScoringResultSnapshotCodec}.
 */
public record PersistedDiagnostic(
        String ruleId,
        String title,
        String description,
        String severity,
        String pointer,
        List<String> paths,
        Integer lineNumber,
        String documentationUrl,
        Double scoreImprovement) {
}
