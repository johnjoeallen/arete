package net.dublinux.arete.engine;

import net.dublinux.arete.engine.api.RuleOutcome;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A matcher can report a measured value with an occurrence, and a policy can charge tiers by it. */
class ValueMeasureTest {
    private static String policy(String rule) {
        return "---\nid: Depth\nformat: 2\nrules:\n  JSON025: " + rule + "\n---\n\n# Depth\n";
    }

    private static Engine engine(Path tmp, String rule) throws IOException {
        Path dir = tmp.resolve("policies");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("depth.md"), policy(rule));
        return Engine.builder().cacheDir(null).userPoliciesDir(dir).build();
    }

    /** A chain of {@code levels} schemas, the first holding the next by reference: the first nests {@code levels} deep. */
    private static String chain(int levels) {
        StringBuilder text = new StringBuilder("openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths: {}\ncomponents:\n  schemas:\n");
        for (int i = 0; i < levels; i++) {
            text.append("    S").append(i).append(":\n      type: object\n      properties:\n");
            text.append(i + 1 < levels ? "        next: { $ref: '#/components/schemas/S" + (i + 1) + "' }\n" : "        leaf: { type: string }\n");
        }
        return text.toString();
    }

    private static ScoringResult score(Engine engine, int levels) {
        return engine.score(SpecInput.builder().content(chain(levels)).format(SpecFormat.OPENAPI3).policy("Depth").build());
    }

    private static RuleOutcome outcome(ScoringResult result) {
        return result.getRuleOutcomes().stream().filter(o -> o.ruleId().equals("JSON025")).findFirst().orElse(null);
    }

    @Test
    void tiersChargeByTheDeepestValueNotByHowManySchemasMatch(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, "{ measure: value, tiers: { 4: 1, 6: 3 } }");

        assertEquals(null, outcome(score(engine, 3)), "below the first tier nothing is reported");
        ScoringResult four = score(engine, 4);
        assertEquals(99.0, four.getOverallScore());
        assertEquals(4.0, outcome(four).measure());
        // seven schemas: S0..S3 are at least 4 deep (7, 6, 5, 4), so four matches, and the worst is 7
        ScoringResult seven = score(engine, 7);
        assertEquals(97.0, seven.getOverallScore());
        assertEquals(7.0, outcome(seven).measure());
        assertEquals(4, outcome(seven).count());
        assertEquals(7.0, seven.getDiagnostics().get(0).getValue() == null ? -1 : seven.getDiagnostics().stream().mapToDouble(d -> d.getValue()).max().orElse(-1));
    }

    @Test
    void measureValueNeedsTiersAndAKnownName(@TempDir Path tmp) {
        assertThrows(Exception.class, () -> engine(tmp.resolve("a"), "{ measure: value, per-match: 1 }"));
        assertThrows(Exception.class, () -> engine(tmp.resolve("b"), "{ measure: size, tiers: { 1: 1 } }"));
    }

    @Test
    void aRuleChargedByValueNeedsAMatcherThatReportsOne(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("policies");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("bad.md"), "---\nid: NoValue\nformat: 2\nrules:\n  DOC001: { measure: value, tiers: { 1: 1 } }\n---\n\n# NoValue\n");
        Engine engine = Engine.builder().cacheDir(null).userPoliciesDir(dir).build();
        ScoringResult result = engine.score(SpecInput.builder().content("openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths:\n  /m:\n    get:\n      responses: { '200': { description: OK } }\n").format(SpecFormat.OPENAPI3).policy("NoValue").build());
        assertEquals(ScoringResult.Status.PLUGIN_ERROR, result.getStatus());
    }

    private static String operationWith(int parameters) {
        StringBuilder text = new StringBuilder("openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths:\n  /reports:\n    get:\n      summary: S\n      parameters:\n");
        for (int i = 0; i < parameters; i++) text.append("        - { name: p").append(i).append(", in: query, schema: { type: string } }\n");
        text.append("      responses: { '200': { description: OK } }\n");
        return text.toString();
    }

    @Test
    void aPolicyCanTierStandard011ByParameterCountFromBelowItsDefaultMaximum(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("policies");
        Files.createDirectories(dir);
        // maximum: 0 makes the matcher report every operation with parameters; the tiers then decide what is tolerated.
        Files.writeString(dir.resolve("p.md"), "---\nid: Params\nformat: 2\nrules:\n  STANDARD011:\n    measure: value\n    tiers: { 5: 0.5, 9: 1, 13: 3 }\n    parameters: { maximum: 0 }\n---\n\n# Params\n");
        Engine engine = Engine.builder().cacheDir(null).userPoliciesDir(dir).build();

        java.util.function.IntFunction<ScoringResult> run = n -> engine.score(
                SpecInput.builder().content(operationWith(n)).format(SpecFormat.OPENAPI3).policy("Params").build());
        assertEquals(null, run.apply(4).getRuleOutcomes().stream().filter(o -> o.ruleId().equals("STANDARD011")).findFirst().orElse(null));
        assertEquals(99.5, run.apply(5).getOverallScore());
        assertEquals(99.0, run.apply(9).getOverallScore());
        assertEquals(97.0, run.apply(14).getOverallScore());
    }

    @Test
    void aPolicyCanTierStatus001ByHowManyPostsLackA201(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("policies");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("p.md"), "---\nid: Creates\nformat: 2\nrules:\n  STATUS001: { tiers: { 2: 1, 5: 3 } }\n---\n\n# Creates\n");
        Engine engine = Engine.builder().cacheDir(null).userPoliciesDir(dir).build();

        java.util.function.IntFunction<ScoringResult> run = n -> {
            StringBuilder text = new StringBuilder("openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths:\n");
            for (int i = 0; i < n; i++) text.append("  /things").append(i).append(":\n    post:\n      summary: S\n      responses: { '200': { description: OK } }\n");
            return engine.score(SpecInput.builder().content(text.toString()).format(SpecFormat.OPENAPI3).policy("Creates").build());
        };
        assertEquals(100.0, run.apply(1).getOverallScore(), "one missing 201 is tolerated");
        assertEquals(99.0, run.apply(2).getOverallScore());
        assertEquals(97.0, run.apply(6).getOverallScore());
        assertEquals(6, run.apply(6).getRuleOutcomes().stream().filter(o -> o.ruleId().equals("STATUS001")).findFirst().get().count());
    }
}
