package net.dublinux.arete.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationResultSnapshotCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void gradeAndPassingScoreSurviveARoundTrip() throws Exception {
        AggregatedValidationResult original = new AggregatedValidationResult(
                List.of(), List.of(), 111, 92.5, 92.5, "B-", 90.0);

        String json = ValidationResultSnapshotCodec.toJson(mapper, original, List.of("generic-policy"));
        AggregatedValidationResult reloaded = ValidationResultSnapshotCodec.fromJson(mapper, json).result();

        assertThat(reloaded.grade()).isEqualTo("B-");
        assertThat(reloaded.passingScore()).isEqualTo(90.0);
        assertThat(reloaded.meetsPassingScore()).isTrue();
        assertThat(reloaded.overallScore()).isEqualTo(92.5);
    }

    @Test
    void aPreGradeSnapshotReloadsWithNoGradeRatherThanFailing() throws Exception {
        String legacyJson = """
                {"activePluginIds":["generic-policy"],"pluginSummaries":[],"diagnostics":[],
                 "rulesEvaluatedCount":111,"overallScore":92.5,"overallScoreWithoutBlockers":92.5}
                """;

        AggregatedValidationResult reloaded = ValidationResultSnapshotCodec.fromJson(mapper, legacyJson).result();

        assertThat(reloaded.grade()).isNull();
        assertThat(Double.isNaN(reloaded.passingScore())).isTrue();
    }
}
