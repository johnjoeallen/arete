package net.dublinux.arete.engine.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locating findings in the text, comparing two runs, and writing the result for people and machines. */
class ReportsTest {
    private static final String YAML = """
            openapi: 3.0.0
            info: { title: Orders, version: 1.0.0 }
            paths:
              /orders:
                get:
                  parameters:
                    - { name: limit, in: query, schema: { type: integer } }
                    - { name: cursor, in: query, schema: { type: string } }
                  responses:
                    '200': { description: OK }
            """;

    private static final String JSON = "{\n  \"paths\": {\n    \"/orders\": {\n      \"get\": {}\n    }\n  }\n}\n";

    private static Engine engine() {
        return Engine.builder().cacheDir(null).build();
    }

    @Test
    void aPointerIsLocatedInYamlAndJson() {
        PointerLocator yaml = PointerLocator.of(YAML);
        assertEquals(new PointerLocator.Location(4, 3), yaml.locate("/paths/~1orders"));
        assertEquals(5, yaml.locate("/paths/~1orders/get").line());
        assertEquals(8, yaml.locate("/paths/~1orders/get/parameters/1").line());
        assertEquals(1, yaml.locate("/").line());

        PointerLocator json = PointerLocator.of(JSON);
        assertEquals(4, json.locate("/paths/~1orders/get").line());
    }

    @Test
    void aMissingPlaceIsLocatedAtItsNearestAncestorAndUnreadableTextAtNowhere() {
        PointerLocator yaml = PointerLocator.of(YAML);
        assertEquals(5, yaml.locate("/paths/~1orders/get/summary").line(), "a summary that is missing belongs on its operation");
        assertEquals(6, yaml.locate("/paths/~1orders/get/parameters/9").line(), "an index past the end falls back to the list");
        assertNull(PointerLocator.of("a: [unclosed").locate("/a"));
        assertNull(PointerLocator.of("").locate("/a"));
    }

    @Test
    void aReportCarriesTheScoreThePassMarkAndTheLinesOfItsFindings() {
        ScoreReport report = ScoreReport.of(engine(), "apis/orders.yaml", YAML, null, Overrides.none());

        assertTrue(report.succeeded());
        assertEquals("Enterprise Grade", report.policy(), "the default policy resolves to its real name");
        assertEquals(90.0, report.passingScore());
        assertFalse(report.findings().isEmpty());
        assertTrue(report.findings().stream().allMatch(f -> f.line() != null && f.line() >= 1));
        List<Integer> lines = report.findings().stream().map(Finding::line).toList();
        assertEquals(lines.stream().sorted().toList(), lines, "findings read in file order");
    }

    @Test
    void aDiffClassifiesFindingsByRuleAndPointerNotMessage() {
        Finding a = new Finding("R1", "t", "WARNING", "Warning", "/p", "one", 1, 1, 0.5);
        Finding aReworded = new Finding("R1", "t", "WARNING", "Warning", "/p", "two (3 of 9)", 4, 1, 0.5);
        Finding b = new Finding("R2", "t", "ERROR", "Blocker", "/q", "gone", 2, 1, 0);
        Finding c = new Finding("R3", "t", "ERROR", "Blocker", "/r", "new", 3, 1, 0);
        ScoreReport base = new ScoreReport("f.yaml", "P", "SUCCESS", null, 99, 99, "A", 90.0, 3, List.of(a, b), List.of());
        ScoreReport head = new ScoreReport("f.yaml", "P", "SUCCESS", null, 95, 95, "A", 90.0, 3, List.of(aReworded, c), List.of());

        ScoreDiff diff = ScoreDiff.of(base, head);

        assertEquals(1, diff.count(ScoreDiff.Kind.NEW));
        assertEquals("R3", diff.of(ScoreDiff.Kind.NEW).get(0).finding().ruleId());
        assertEquals(1, diff.count(ScoreDiff.Kind.EXISTING));
        assertEquals(1, diff.count(ScoreDiff.Kind.RESOLVED));
        assertEquals("R2", diff.of(ScoreDiff.Kind.RESOLVED).get(0).finding().ruleId());
        assertEquals(-4.0, diff.scoreDelta());
        assertTrue(diff.regressed());
        assertEquals(1, diff.newBlockers());
    }

    @Test
    void findingsThatShareARuleAndPointerPairOffOneForOne() {
        Finding f = new Finding("R", "t", "WARNING", "Warning", "/op", "m", 1, 1, 0.5);
        ScoreReport base = new ScoreReport("f", "P", "SUCCESS", null, 99, 99, "A", null, 1, List.of(f, f), List.of());
        ScoreReport head = new ScoreReport("f", "P", "SUCCESS", null, 98, 98, "A", null, 1, List.of(f, f, f), List.of());

        ScoreDiff diff = ScoreDiff.of(base, head);

        assertEquals(2, diff.count(ScoreDiff.Kind.EXISTING));
        assertEquals(1, diff.count(ScoreDiff.Kind.NEW));
        assertEquals(0, diff.count(ScoreDiff.Kind.RESOLVED));
    }

    @Test
    void markdownIsOneLinePerFindingNewFirstAndEscapesPipes() {
        Finding oldOne = new Finding("R1", "t", "WARNING", "Warning", "/p", "stays | here", 1, 1, 0.5);
        Finding newOne = new Finding("R3", "t", "ERROR", "Blocker", "/r", "arrived", 3, 1, 0);
        ScoreReport base = new ScoreReport("f.yaml", "P", "SUCCESS", null, 99.5, 99.5, "A", 90.0, 3, List.of(oldOne), List.of());
        ScoreReport head = new ScoreReport("f.yaml", "P", "SUCCESS", null, 0, 99.5, "F", 90.0, 3, List.of(oldOne, newOne), List.of());

        String comment = ReportWriter.markdown(ScoreDiff.of(base, head));
        String[] lines = comment.split("\n");
        List<String> findingLines = java.util.Arrays.stream(lines).filter(l -> l.startsWith("- **")).toList();

        assertEquals(2, findingLines.size());
        assertTrue(findingLines.get(0).startsWith("- **NEW** Blocker `R3` `/r` (f.yaml:3)"), findingLines.get(0));
        assertTrue(findingLines.get(1).startsWith("- **EXISTING** Warning `R1`"), findingLines.get(1));
        assertTrue(comment.contains("score 99.5 -> 0 (-99.5)"), comment);
        assertTrue(comment.contains("pass mark 90: FAIL"), comment);
        assertTrue(comment.contains("1 new, 1 existing, 0 resolved"), comment);

        String table = ReportWriter.markdown(List.of(head), false);
        assertTrue(table.contains("stays \\| here"), table);
    }

    @Test
    void aLongRunOfUnchangedFindingsIsCappedInTheComment() {
        List<Finding> many = new java.util.ArrayList<>();
        for (int i = 0; i < ReportWriter.MAX_EXISTING_IN_COMMENT + 5; i++) many.add(new Finding("R", "t", "WARNING", "Warning", "/p" + i, "m", i + 1, 1, 0.5));
        ScoreReport report = new ScoreReport("f", "P", "SUCCESS", null, 90, 90, "B", null, 1, many, List.of());

        String comment = ReportWriter.markdown(ScoreDiff.of(report, report));

        assertEquals(ReportWriter.MAX_EXISTING_IN_COMMENT, comment.lines().filter(l -> l.startsWith("- **EXISTING**")).count());
        assertTrue(comment.contains("… and 5 more existing findings"), comment);
    }

    @Test
    void jsonIsVersionedAndComplete() throws Exception {
        ScoreReport report = ScoreReport.of(engine(), "orders.yaml", YAML, "Zalando", Overrides.none());
        JsonNode root = new ObjectMapper().readTree(ReportWriter.json(List.of(report)));

        assertEquals(ReportWriter.JSON_SCHEMA_VERSION, root.get("schemaVersion").asInt());
        assertEquals("score", root.get("kind").asText());
        JsonNode spec = root.get("specs").get(0);
        assertEquals("orders.yaml", spec.get("file").asText());
        assertEquals("Zalando", spec.get("policy").asText());
        assertTrue(spec.has("score") && spec.has("meetsPassingScore") && spec.get("findings").isArray());
        assertTrue(spec.get("findings").get(0).has("line"));

        JsonNode diff = new ObjectMapper().readTree(ReportWriter.json(ScoreDiff.of(report, report)));
        assertEquals("diff", diff.get("kind").asText());
        assertEquals(0, diff.get("counts").get("new").asInt());
        assertEquals(0.0, diff.get("scoreDelta").asDouble());
    }

    @Test
    void sarifPutsEachFindingOnItsFileAndLine() throws Exception {
        ScoreReport report = ScoreReport.of(engine(), "apis/orders.yaml", YAML, null, Overrides.none());
        JsonNode sarif = new ObjectMapper().readTree(ReportWriter.sarif(List.of(report)));

        assertEquals("2.1.0", sarif.get("version").asText());
        JsonNode first = sarif.get("runs").get(0).get("results").get(0);
        assertEquals("apis/orders.yaml", first.get("locations").get(0).get("physicalLocation").get("artifactLocation").get("uri").asText());
        assertTrue(first.get("locations").get(0).get("physicalLocation").get("region").get("startLine").asInt() >= 1);
        assertNotNull(first.get("locations").get(0).get("logicalLocations"));
        assertTrue(sarif.get("runs").get(0).get("tool").get("driver").get("rules").size() > 0);
    }

    @Test
    void aDiffsSarifListsOnlyWhatTheChangeIntroduced() throws Exception {
        Finding oldOne = new Finding("R1", "t", "WARNING", "Warning", "/p", "m", 1, 1, 0.5);
        Finding newOne = new Finding("R3", "t", "ERROR", "Blocker", "/r", "m", 3, 1, 0);
        ScoreReport base = new ScoreReport("f.yaml", "P", "SUCCESS", null, 99, 99, "A", null, 2, List.of(oldOne), List.of());
        ScoreReport head = new ScoreReport("f.yaml", "P", "SUCCESS", null, 0, 99, "F", null, 2, List.of(oldOne, newOne), List.of());

        JsonNode sarif = new ObjectMapper().readTree(ReportWriter.sarif(ScoreDiff.of(base, head)));

        assertEquals(1, sarif.get("runs").get(0).get("results").size());
        assertEquals("R3", sarif.get("runs").get(0).get("results").get(0).get("ruleId").asText());
        assertEquals("error", sarif.get("runs").get(0).get("results").get(0).get("level").asText());
    }

    @Test
    void anErrorReportSaysWhatWentWrongInsteadOfAScore() {
        ScoreReport report = ScoreReport.of(engine(), "broken.yaml", "openapi: 3.0.0\ninfo: [", null, Overrides.none());

        assertFalse(report.succeeded());
        assertTrue(ReportWriter.text(List.of(report)).contains("PARSE_ERROR"));
        assertTrue(ReportWriter.markdown(List.of(report), false).contains("PARSE_ERROR"));
    }
}
