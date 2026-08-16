package com.speculate.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dublinux.speculate.validation.spi.Severity;
import net.dublinux.speculate.validation.spi.Violation;

import java.util.List;

/**
 * Converts between the live {@link AggregatedValidationResult} (built from
 * SPI types a plugin jar controls) and a JSON snapshot suitable for {@link
 * SpecValidationResultEntity} — the "reload the last run when a spec is
 * reopened" feature needs the result to outlive the request that computed
 * it. {@link Violation} isn't itself Jackson-friendly (a plain builder
 * class, deliberately not a record — see its own javadoc — with no default
 * constructor), so this rebuilds one via {@link Violation#builder()} on the
 * way back rather than annotating the SPI class itself.
 */
final class ValidationResultSnapshotCodec {

    private ValidationResultSnapshotCodec() {
    }

    static String toJson(ObjectMapper mapper, AggregatedValidationResult result, List<String> activePluginIds)
            throws JsonProcessingException {
        return mapper.writeValueAsString(toPersisted(result, activePluginIds));
    }

    static CachedValidationResult fromJson(ObjectMapper mapper, String json) throws JsonProcessingException {
        PersistedAggregatedValidationResult persisted = mapper.readValue(json, PersistedAggregatedValidationResult.class);
        return new CachedValidationResult(toLive(persisted), persisted.activePluginIds());
    }

    private static PersistedAggregatedValidationResult toPersisted(AggregatedValidationResult result, List<String> activePluginIds) {
        return new PersistedAggregatedValidationResult(
                activePluginIds,
                result.pluginSummaries().stream()
                        .map(s -> new PersistedValidationSummary(s.pluginName(), s.status(), s.violationCount(), s.errorMessage()))
                        .toList(),
                result.violations().stream()
                        .map(ValidationResultSnapshotCodec::toPersisted)
                        .toList(),
                result.rulesEvaluatedCount(),
                nullIfNaN(result.overallScore()),
                nullIfNaN(result.overallScoreWithoutBlockers()));
    }

    private static PersistedAttributedViolation toPersisted(AttributedViolation av) {
        Violation v = av.violation();
        PersistedViolation pv = new PersistedViolation(
                v.getRuleId(), v.getTitle(), v.getDescription(), v.getSeverity().name(), v.getPointer(),
                v.getPaths(), v.getLineNumber(), v.getDocumentationUrl(), nullIfNaN(v.getScoreImprovement()));
        return new PersistedAttributedViolation(av.pluginId(), av.pluginName(), pv);
    }

    private static AggregatedValidationResult toLive(PersistedAggregatedValidationResult persisted) {
        List<ValidationSummary> summaries = persisted.pluginSummaries().stream()
                .map(s -> new ValidationSummary(s.pluginName(), s.status(), s.violationCount(), s.errorMessage()))
                .toList();
        List<AttributedViolation> violations = persisted.violations().stream()
                .map(ValidationResultSnapshotCodec::toLive)
                .toList();
        return new AggregatedValidationResult(
                summaries, violations, persisted.rulesEvaluatedCount(),
                nanIfNull(persisted.overallScore()), nanIfNull(persisted.overallScoreWithoutBlockers()));
    }

    private static AttributedViolation toLive(PersistedAttributedViolation pav) {
        PersistedViolation pv = pav.violation();
        Violation violation = Violation.builder()
                .ruleId(pv.ruleId())
                .title(pv.title())
                .description(pv.description())
                .severity(Severity.valueOf(pv.severity()))
                .pointer(pv.pointer())
                .paths(pv.paths() == null ? List.of() : pv.paths())
                .lineNumber(pv.lineNumber())
                .documentationUrl(pv.documentationUrl())
                .scoreImprovement(nanIfNull(pv.scoreImprovement()))
                .build();
        return new AttributedViolation(pav.pluginId(), pav.pluginName(), violation);
    }

    private static Double nullIfNaN(double d) {
        return Double.isNaN(d) ? null : d;
    }

    private static double nanIfNull(Double d) {
        return d == null ? Double.NaN : d;
    }
}
