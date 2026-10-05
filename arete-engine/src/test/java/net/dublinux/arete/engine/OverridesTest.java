package net.dublinux.arete.engine;

import net.dublinux.arete.engine.api.Diagnostic;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A team's .arete.yaml: parsed strictly, and applied without touching the bundle or any locked rule. */
class OverridesTest {
    private static Engine engine(Path tmp, boolean withLockedOverlay) {
        MiniBundle.writeDir(tmp.resolve("base"), MiniBundle.base("1.0.0"));
        Engine.Builder builder = Engine.builder().policySource("file:" + tmp.resolve("base")).cacheDir(null);
        if (withLockedOverlay) {
            MiniBundle.writeDir(tmp.resolve("org"), MiniBundle.overlay("1.0.0"));
            builder.policySource("file:" + tmp.resolve("org"));
        }
        return builder.build();
    }

    private static ScoringResult score(Engine engine, Overrides overrides) {
        return engine.score(SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Mini").build(), overrides);
    }

    private static Set<String> rules(ScoringResult result) {
        Set<String> rules = new TreeSet<>();
        for (Diagnostic d : result.getDiagnostics()) rules.add(d.getRuleId());
        return rules;
    }

    @Test
    void aValidFileIsParsed() {
        Overrides overrides = Overrides.parse("""
                policy: Mini
                overrides:
                  DOC001:
                    reason: Summaries are generated.
                    disable: true
                  DOC015:
                    reason: Short descriptions are fine here.
                    points: 1
                    parameters: { method: GET }
                """);
        assertEquals("Mini", overrides.policy());
        assertEquals(2, overrides.rules().size());
        assertTrue(overrides.rules().get("DOC001").disabled());
        assertEquals(1.0, overrides.rules().get("DOC015").points());
        assertEquals("GET", overrides.rules().get("DOC015").parameters().get("method"));
        assertTrue(Overrides.parse("").isEmpty());
    }

    @Test
    void everyOverrideNeedsAReasonAndSomethingToDo() {
        assertThrows(BundleValidationException.class, () -> Overrides.parse("overrides:\n  DOC001:\n    disable: true\n"));
        assertThrows(BundleValidationException.class, () -> Overrides.parse("overrides:\n  DOC001:\n    reason: ' '\n    disable: true\n"));
        assertThrows(BundleValidationException.class, () -> Overrides.parse("overrides:\n  DOC001:\n    reason: because\n"));
        assertThrows(BundleValidationException.class,
                () -> Overrides.parse("overrides:\n  DOC001:\n    reason: because\n    disable: true\n    points: 1\n"));
    }

    @Test
    void unknownKeysAndBadValuesAreRefused() {
        assertThrows(BundleValidationException.class, () -> Overrides.parse("overide:\n  DOC001: {}\n"));
        assertThrows(BundleValidationException.class,
                () -> Overrides.parse("overrides:\n  DOC001:\n    reason: because\n    points: 150\n"));
        assertThrows(BundleValidationException.class,
                () -> Overrides.parse("overrides:\n  DOC001:\n    reason: because\n    colour: red\n"));
        assertThrows(BundleValidationException.class, () -> Overrides.parse("overrides: [DOC001]\n"));
    }

    @Test
    void disablingARuleSilencesItAndLeavesTheBundleAlone(@TempDir Path tmp) {
        Engine engine = engine(tmp, false);
        Overrides off = Overrides.parse("overrides:\n  DOC001:\n    reason: Summaries are generated.\n    disable: true\n");

        ScoringResult with = score(engine, off);
        assertEquals(Set.of(), rules(with));
        assertEquals(100.0, with.getOverallScore());
        assertEquals(Set.of("DOC001"), rules(score(engine, Overrides.none())), "the override must not leak into the bundle");
    }

    @Test
    void pointsAndParametersChangeTheRunNotTheBundle(@TempDir Path tmp) {
        Engine engine = engine(tmp, false);

        ScoringResult heavy = score(engine, Overrides.parse("overrides:\n  DOC001:\n    reason: Matters more to us.\n    points: 10\n"));
        assertEquals(90.0, heavy.getOverallScore());

        ScoringResult narrowed = score(engine, Overrides.parse(
                "overrides:\n  DOC001:\n    reason: Only deletes need one.\n    parameters: { method: DELETE }\n"));
        assertEquals(Set.of(), rules(narrowed));
        assertEquals(99.0, score(engine, Overrides.none()).getOverallScore());
    }

    @Test
    void aLockedRuleCannotBeOverridden(@TempDir Path tmp) {
        Engine engine = engine(tmp, true);

        ScoringResult locked = score(engine, Overrides.parse("overrides:\n  DOC001:\n    reason: Please.\n    disable: true\n"));
        assertEquals(ScoringResult.Status.ENGINE_ERROR, locked.getStatus());
        assertTrue(locked.getErrorMessage().contains("locks this rule"), locked.getErrorMessage());

        // The unlocked rule beside it can be.
        ScoringResult ok = score(engine, Overrides.parse("overrides:\n  DOC015:\n    reason: Not for this team.\n    disable: true\n"));
        assertEquals(Set.of("DOC001"), rules(ok));
    }

    @Test
    void anOverrideOfAnUnknownRuleOrBadParameterIsAnErrorNotANoOp(@TempDir Path tmp) {
        Engine engine = engine(tmp, false);

        ScoringResult unknown = score(engine, Overrides.parse("overrides:\n  NOPE001:\n    reason: Typo.\n    disable: true\n"));
        assertEquals(ScoringResult.Status.ENGINE_ERROR, unknown.getStatus());
        assertTrue(unknown.getErrorMessage().contains("no such rule"), unknown.getErrorMessage());

        ScoringResult badParameter = score(engine, Overrides.parse(
                "overrides:\n  DOC001:\n    reason: Because.\n    parameters: { method: FETCH }\n"));
        assertEquals(ScoringResult.Status.ENGINE_ERROR, badParameter.getStatus());
    }

    @Test
    void anOverrideOfARuleThePolicyDoesNotRunDoesNothing() {
        Engine engine = Engine.builder().cacheDir(null).build();   // the public bundle; Zalando does not run DOC001
        Overrides off = Overrides.parse("overrides:\n  DOC001:\n    reason: Not our concern.\n    disable: true\n");
        SpecInput input = SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Zalando").build();

        assertEquals(engine.score(input).getDiagnostics().size(), engine.score(input, off).getDiagnostics().size());
        assertEquals(ScoringResult.Status.SUCCESS, engine.score(input, off).getStatus());
    }
}
