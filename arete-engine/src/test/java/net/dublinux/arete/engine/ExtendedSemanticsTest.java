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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A policy can charge by how many times a rule matches, and can ask for a match instead of forbidding one. */
class ExtendedSemanticsTest {
    private static String policy(String id, String rule) {
        return "---\nid: " + id + "\nformat: 2\nrules:\n  DOC001: " + rule + "\n---\n\n# " + id + "\n";
    }

    private static Engine engine(Path tmp, String... policies) throws IOException {
        MiniBundle.writeDir(tmp.resolve("base"), MiniBundle.base("1.0.0"));
        Path dir = tmp.resolve("policies");
        Files.createDirectories(dir);
        for (int i = 0; i < policies.length; i++) Files.writeString(dir.resolve("p" + i + ".md"), policies[i]);
        return Engine.builder().policySource("file:" + tmp.resolve("base")).cacheDir(null).userPoliciesDir(dir).build();
    }

    /** A spec with {@code missing} operations lacking a summary (DOC001 matches each) and one that has it. */
    private static String spec(int missing) {
        StringBuilder text = new StringBuilder("openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths:\n  /documented:\n    get:\n      summary: Documented\n      responses: { '200': { description: OK } }\n");
        for (int i = 0; i < missing; i++) text.append("  /m").append(i).append(":\n    get:\n      responses: { '200': { description: OK } }\n");
        return text.toString();
    }

    private static ScoringResult score(Engine engine, String policy, int missing) {
        return engine.score(SpecInput.builder().content(spec(missing)).format(SpecFormat.OPENAPI3).policy(policy).build());
    }

    @Test
    void perMatchChargesEachMatchUpToTheCap(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("PerMatch", "{ per-match: 0.5, max: 1 }"));

        assertEquals(99.5, score(engine, "PerMatch", 1).getOverallScore());
        assertEquals(99.0, score(engine, "PerMatch", 2).getOverallScore());
        assertEquals(99.0, score(engine, "PerMatch", 5).getOverallScore(), "capped at 1");
        assertEquals(5, score(engine, "PerMatch", 5).getDiagnostics().size(), "every match is still reported");
        assertEquals(100.0, score(engine, "PerMatch", 0).getOverallScore());
    }

    @Test
    void perMatchWithoutACapIsLimitedOnlyByTheScoreFloor(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("Uncapped", "{ per-match: 30 }"));

        assertEquals(70.0, score(engine, "Uncapped", 1).getOverallScore());
        assertEquals(0.0, score(engine, "Uncapped", 4).getOverallScore());
    }

    @Test
    void tiersChargeByTheHighestTierReachedAndNothingBelowTheFirst(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("Tiered", "{ tiers: { 3: 4, 2: 1 } }"));

        ScoringResult one = score(engine, "Tiered", 1);
        assertEquals(100.0, one.getOverallScore());
        assertEquals(0, one.getDiagnostics().size(), "below the first tier the rule is not violated, so it reports nothing");
        assertEquals(99.0, score(engine, "Tiered", 2).getOverallScore());
        assertEquals(96.0, score(engine, "Tiered", 3).getOverallScore());
        assertEquals(96.0, score(engine, "Tiered", 7).getOverallScore());
    }

    @Test
    void theResultSaysHowManyTimesEachRuleMatchedAndWhatItCost(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("PerMatch", "{ per-match: 0.5, max: 1 }"), policy("Flat", "0.5"));

        RuleOutcome graduated = score(engine, "PerMatch", 5).getRuleOutcomes().get(0);
        assertEquals(new RuleOutcome("DOC001", 5, 1.0, true, false), graduated);
        RuleOutcome flat = score(engine, "Flat", 5).getRuleOutcomes().get(0);
        assertEquals(new RuleOutcome("DOC001", 5, 0.5, false, false), flat);
    }

    @Test
    void expectMatchMakesTheAbsenceTheViolation(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("Needed", "{ expect: match, points: 3 }"));

        ScoringResult present = score(engine, "Needed", 1);
        assertEquals(100.0, present.getOverallScore(), "a match was found, which is what was asked for");
        assertEquals(0, present.getDiagnostics().size());

        ScoringResult absent = score(engine, "Needed", 0);
        assertEquals(97.0, absent.getOverallScore());
        assertEquals(1, absent.getDiagnostics().size());
        assertEquals("/", absent.getDiagnostics().get(0).getPointer());
        assertTrue(absent.getDiagnostics().get(0).getDescription().startsWith("Expected at least one match but found none"), absent.getDiagnostics().get(0).getDescription());
    }

    @Test
    void expectMatchCanBeProhibitedAndNoMatchIsTheDefault(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("Required", "{ expect: match, points: PROHIBITED }"), policy("Explicit", "{ expect: no-match, points: 2 }"));

        assertEquals(0.0, score(engine, "Required", 0).getOverallScore());
        assertEquals(net.dublinux.arete.engine.api.Severity.ERROR, score(engine, "Required", 0).getDiagnostics().get(0).getSeverity());
        assertEquals(100.0, score(engine, "Required", 1).getOverallScore());
        assertEquals(98.0, score(engine, "Explicit", 1).getOverallScore());
    }

    @Test
    void theNewKeysNeedFormatTwoAndAreChecked(@TempDir Path tmp) {
        assertInvalid(tmp, "---\nid: P\nrules:\n  DOC001: { per-match: 1 }\n---\n", "needs format: 2");
        assertInvalid(tmp, policy("P", "{ points: 1, per-match: 1 }"), "exactly one of points, per-match or tiers");
        assertInvalid(tmp, policy("P", "{ expect: match }"), "exactly one of points, per-match or tiers");
        assertInvalid(tmp, policy("P", "{ points: 1, max: 2 }"), "max only goes with per-match");
        assertInvalid(tmp, policy("P", "{ per-match: 0 }"), "per-match must be a number above 0");
        assertInvalid(tmp, policy("P", "{ tiers: { many: 1 } }"), "not a count");
        assertInvalid(tmp, policy("P", "{ tiers: { 0: 1 } }"), "1 or more");
        assertInvalid(tmp, policy("P", "{ tiers: { 2: 150 } }"), "from 0 to 100");
        assertInvalid(tmp, policy("P", "{ tiers: {} }"), "map from a count to points");
        assertInvalid(tmp, policy("P", "{ expect: sometimes, points: 1 }"), "expect must be match or no-match");
        assertInvalid(tmp, "---\nid: P\nformat: 3\nrules:\n  DOC001: 1\n---\n", "format must be 1 or 2");
    }

    private void assertInvalid(Path tmp, String policy, String message) {
        BundleValidationException e = assertThrows(BundleValidationException.class, () -> engine(tmp, policy));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void anOverrideKeepsAGraduatedRuleGraduatedUnlessItSetsPoints(@TempDir Path tmp) throws IOException {
        Engine engine = engine(tmp, policy("PerMatch", "{ per-match: 0.5, max: 1 }"));
        SpecInput input = SpecInput.builder().content(spec(5)).format(SpecFormat.OPENAPI3).policy("PerMatch").build();

        Overrides narrowed = Overrides.parse("overrides:\n  DOC001:\n    reason: Only deletes need one.\n    parameters: { method: DELETE }\n");
        assertEquals(100.0, engine.score(input, narrowed).getOverallScore());

        Overrides flat = Overrides.parse("overrides:\n  DOC001:\n    reason: Flat is enough here.\n    points: 5\n");
        ScoringResult result = engine.score(input, flat);
        assertEquals(95.0, result.getOverallScore());
        assertEquals(false, result.getRuleOutcomes().get(0).graduated());
    }
}
