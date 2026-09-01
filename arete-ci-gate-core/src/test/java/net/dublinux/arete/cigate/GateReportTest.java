package net.dublinux.arete.cigate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GateReportTest {

    private GateRequest request() {
        return GateRequest.builder()
                .areteBaseUrl("http://localhost:6809")
                .namespace("payments")
                .submitter("maven")
                .spec("openapi: 3.0.0")
                .specDisplayName("my-service")
                .combination(Combination.gating("generic-policy", "Enterprise Grade"))
                .build();
    }

    @Test
    void showsEveryRowAndNamesTheFailingGatingCombination() {
        GateOutcome outcome = new GateOutcome("id", List.of(
                new CombinationOutcome("generic-policy", "Enterprise Grade", false, "SUCCESS",
                        71.0, "D", 90.0, "score<90", "policy", false, Map.of("error", 2)),
                new CombinationOutcome("generic-policy", "Zalando", true, "SUCCESS",
                        95.0, "A", null, "error", "policy", true, Map.of())), null);

        String report = GateReport.render(request(), outcome);

        assertTrue(report.contains("Areté CI Gate — my-service"));
        assertTrue(report.contains("generic-policy/Enterprise Grade"));
        assertTrue(report.contains("no (optional)"));
        assertTrue(report.contains("Overall: FAIL"));
        assertTrue(report.contains("failing: generic-policy/Enterprise Grade"));
    }

    @Test
    void reportsPassWhenNoGatingCombinationFails() {
        GateOutcome outcome = new GateOutcome("id", List.of(
                new CombinationOutcome("generic-policy", "Enterprise Grade", false, "SUCCESS",
                        93.5, "B+", 90.0, "score<90", "policy", true, Map.of())), null);

        assertTrue(GateReport.render(request(), outcome).contains("Overall: PASS"));
    }
}
