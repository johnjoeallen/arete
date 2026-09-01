package net.dublinux.arete.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dublinux.arete.scoring.spi.Severity;
import net.dublinux.arete.scoring.spi.Diagnostic;

import java.util.List;

/**
 * Converts between the live {@link AggregatedScoringResult} (built from
 * SPI types a plugin jar controls) and a JSON snapshot suitable for {@link
 * SpecScoringResultEntity} — the "reload the last run when a spec is
 * reopened" feature needs the result to outlive the request that computed
 * it. {@link Diagnostic} isn't itself Jackson-friendly (a plain builder
 * class, deliberately not a record — see its own javadoc — with no default
 * constructor), so this rebuilds one via {@link Diagnostic#builder()} on the
 * way back rather than annotating the SPI class itself.
 */
final class ScoringResultSnapshotCodec {

    private ScoringResultSnapshotCodec() {
    }

    static String toJson(ObjectMapper mapper, AggregatedScoringResult result, List<String> activePluginIds)
            throws JsonProcessingException {
        return mapper.writeValueAsString(toPersisted(result, activePluginIds));
    }

    static CachedScoringResult fromJson(ObjectMapper mapper, String json) throws JsonProcessingException {
        PersistedAggregatedScoringResult persisted = mapper.readValue(json, PersistedAggregatedScoringResult.class);
        return new CachedScoringResult(toLive(persisted), persisted.activePluginIds());
    }

    private static PersistedAggregatedScoringResult toPersisted(AggregatedScoringResult result, List<String> activePluginIds) {
        return new PersistedAggregatedScoringResult(
                activePluginIds,
                result.pluginSummaries().stream()
                        .map(s -> new PersistedScoringSummary(s.pluginName(), s.status(), s.diagnosticCount(), s.errorMessage()))
                        .toList(),
                result.diagnostics().stream()
                        .map(ScoringResultSnapshotCodec::toPersisted)
                        .toList(),
                result.rulesEvaluatedCount(),
                nullIfNaN(result.overallScore()),
                nullIfNaN(result.overallScoreWithoutBlockers()),
                result.grade(),
                nullIfNaN(result.passingScore()));
    }

    private static PersistedAttributedDiagnostic toPersisted(AttributedDiagnostic av) {
        Diagnostic v = av.diagnostic();
        PersistedDiagnostic pv = new PersistedDiagnostic(
                v.getRuleId(), v.getTitle(), v.getDescription(), v.getSeverity().name(), v.getPointer(),
                v.getPaths(), v.getLineNumber(), v.getDocumentationUrl(), nullIfNaN(v.getScoreImprovement()));
        return new PersistedAttributedDiagnostic(av.pluginId(), av.pluginName(), pv);
    }

    private static AggregatedScoringResult toLive(PersistedAggregatedScoringResult persisted) {
        List<ScoringSummary> summaries = persisted.pluginSummaries().stream()
                .map(s -> new ScoringSummary(s.pluginName(), s.status(), s.diagnosticCount(), s.errorMessage()))
                .toList();
        List<AttributedDiagnostic> diagnostics = persisted.diagnostics().stream()
                .map(ScoringResultSnapshotCodec::toLive)
                .toList();
        return new AggregatedScoringResult(
                summaries, diagnostics, persisted.rulesEvaluatedCount(),
                nanIfNull(persisted.overallScore()), nanIfNull(persisted.overallScoreWithoutBlockers()),
                persisted.grade(), nanIfNull(persisted.passingScore()));
    }

    private static AttributedDiagnostic toLive(PersistedAttributedDiagnostic pav) {
        PersistedDiagnostic pv = pav.diagnostic();
        Diagnostic diagnostic = Diagnostic.builder()
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
        return new AttributedDiagnostic(pav.pluginId(), pav.pluginName(), diagnostic);
    }

    private static Double nullIfNaN(double d) {
        return Double.isNaN(d) ? null : d;
    }

    private static double nanIfNull(Double d) {
        return d == null ? Double.NaN : d;
    }
}
