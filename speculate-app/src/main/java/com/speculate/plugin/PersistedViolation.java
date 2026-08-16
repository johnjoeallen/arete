package com.speculate.plugin;

import java.util.List;

/**
 * JSON-serializable mirror of {@link net.dublinux.speculate.validation.spi.Violation},
 * for storing a validation run's findings in {@link SpecValidationResultEntity}.
 * A plain DTO rather than annotating the SPI class itself, so the SPI stays
 * free of any host-persistence concern. {@code scoreImprovement} is {@code
 * null} for {@link Double#NaN} ("not computed") — see {@link
 * ValidationResultSnapshotCodec}.
 */
public record PersistedViolation(
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
