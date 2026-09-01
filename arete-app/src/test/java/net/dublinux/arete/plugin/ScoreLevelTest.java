package net.dublinux.arete.plugin;

import net.dublinux.arete.scoring.spi.Diagnostic;
import net.dublinux.arete.scoring.spi.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreLevelTest {

    @Test
    void parsesTheGrammar() {
        assertThat(ScoreLevel.parse("never").kind()).isEqualTo(ScoreLevel.Kind.NEVER);
        assertThat(ScoreLevel.parse("").kind()).isEqualTo(ScoreLevel.Kind.NEVER);
        assertThat(ScoreLevel.parse("error").kind()).isEqualTo(ScoreLevel.Kind.ERROR);
        assertThat(ScoreLevel.parse("blocker").kind()).isEqualTo(ScoreLevel.Kind.BLOCKER);
        assertThat(ScoreLevel.parse("score<90").minScore()).isEqualTo(90.0);
        assertThat(ScoreLevel.parse("score<90").describe()).isEqualTo("score<90");
        assertThatThrownBy(() -> ScoreLevel.parse("score<abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoreLevel.parse("bogus")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluatesAgainstAResult() {
        AggregatedScoringResult clean = result(95.0, 95.0, null);
        AggregatedScoringResult warned = result(88.0, 88.0, Severity.WARNING);
        AggregatedScoringResult errored = result(70.0, 70.0, Severity.ERROR);
        AggregatedScoringResult blocked = result(0.0, 60.0, Severity.ERROR);

        assertThat(ScoreLevel.NEVER.failedBy(errored)).isFalse();

        assertThat(ScoreLevel.ERROR.failedBy(warned)).isFalse();
        assertThat(ScoreLevel.ERROR.failedBy(errored)).isTrue();

        assertThat(ScoreLevel.BLOCKER.failedBy(errored)).isFalse();   // score not collapsed
        assertThat(ScoreLevel.BLOCKER.failedBy(blocked)).isTrue();    // 60 -> 0

        assertThat(ScoreLevel.parse("score<90").failedBy(clean)).isFalse();
        assertThat(ScoreLevel.parse("score<90").failedBy(warned)).isTrue();
    }

    private static AggregatedScoringResult result(double score, double withoutBlockers, Severity severity) {
        List<AttributedDiagnostic> diags = severity == null ? List.of()
                : List.of(new AttributedDiagnostic("p", "P",
                        Diagnostic.builder().ruleId("R").title("t").description("m").severity(severity).build()));
        return new AggregatedScoringResult(List.of(), diags, 1, score, withoutBlockers);
    }
}
