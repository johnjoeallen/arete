package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecInput;
import net.dublinux.arete.scoring.spi.ScoringResult;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden-file behaviour snapshots. Every bundled rule (its matcher, scope, and
 * loaded parameters) is run against each shared fixture spec and its findings
 * are rendered deterministically; a separate snapshot records each policy's
 * end-to-end score. A matcher, rule, or scoring change that alters output shows
 * up here as a reviewable diff — no second implementation to keep in sync.
 *
 * <p>After an intended change, regenerate and review the diff:
 * <pre>
 * mvn -pl arete-policy-plugin test -Dtest=PolicySnapshotTest -Dsnapshot.update=true
 * </pre>
 */
class PolicySnapshotTest {

    private static final boolean UPDATE = Boolean.getBoolean("snapshot.update");
    private static final Path SNAPSHOT_DIR = Path.of("src", "test", "resources", "snapshots");
    private static final String REGEN =
            "# regenerate: mvn -pl arete-policy-plugin test -Dtest=PolicySnapshotTest -Dsnapshot.update=true";

    private record Fixture(String name, String spec) { }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("catalogue", ParityFixtures.CATALOGUE_SPEC),
            new Fixture("lint", ParityFixtures.LINT_SPEC),
            new Fixture("house-style", ParityFixtures.HOUSE_STYLE_SPEC),
            new Fixture("schema", ParityFixtures.SCHEMA_SPEC),
            new Fixture("ops", ParityFixtures.OPS_SPEC));

    private static PolicyBundle bundle() {
        return new PolicyBundleLoader().load(
                new ClasspathBundleResources(PolicySnapshotTest.class.getClassLoader()),
                new PolicyBundleLoader.LoadOptions(List.of("distill")));
    }

    @TestFactory
    Stream<DynamicTest> everyBundledRuleMatchesItsSnapshot() {
        PolicyBundle bundle = bundle();
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        return FIXTURES.stream().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
            var parsed = new OpenAPIV3Parser().readContents(fixture.spec(), null, new ParseOptions());
            var api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), fixture.spec());

            StringBuilder out = new StringBuilder()
                    .append("# ").append(fixture.name()).append(" — bundled-rule findings (Distill)\n")
                    .append(REGEN).append('\n');

            for (PolicyRule rule : bundle.rules().values()) {
                Matcher matcher = bundle.matchers().get(rule.matcherId());
                if (matcher == null) continue; // catalogue rule whose matcher isn't bundled yet
                out.append('\n').append("## ").append(rule.id())
                        .append("  (").append(rule.matcherId()).append(" / ").append(rule.scope()).append(")\n");

                List<String> lines;
                try {
                    lines = new ArrayList<>(distill.execute(matcher, api, rule).stream()
                            .map(d -> "  " + str(d.pointer()) + "  |  " + str(d.path()) + "  |  " + str(d.message()))
                            .sorted()
                            .toList());
                } catch (RuntimeException e) {
                    lines = List.of("  ERROR: " + e.getMessage());
                }
                if (lines.isEmpty()) out.append("  (no findings)\n");
                else lines.forEach(l -> out.append(l).append('\n'));
            }

            assertSnapshot(fixture.name() + ".txt", out.toString());
        }));
    }

    @Test
    void policyScoresMatchSnapshot() {
        PolicyScoringPlugin plugin = new PolicyScoringPlugin();
        // Bundled policies only — ignore any ~/.arete/policies on the dev machine
        // so the snapshot is identical here and in CI.
        plugin.configure(Map.of("policies-dir", "target/no-such-policies-dir"));
        List<String> ruleSets = new ArrayList<>(plugin.getRuleSets());
        ruleSets.sort(String::compareTo);

        StringBuilder out = new StringBuilder("# policy score by fixture spec\n").append(REGEN).append('\n');
        for (String ruleSet : ruleSets) {
            out.append('\n').append("## ").append(ruleSet).append('\n');
            for (Fixture fixture : FIXTURES) {
                ScoringResult r = plugin.score(SpecInput.builder()
                        .content(fixture.spec()).format(SpecFormat.OPENAPI3).ruleSet(ruleSet).build());
                out.append(String.format("  %-12s status=%-8s score=%-6s noBlockers=%-6s grade=%-4s findings=%d%n",
                        fixture.name(), r.getStatus(),
                        num(r.getOverallScore()), num(r.getOverallScoreWithoutBlockers()),
                        r.getGrade() == null ? "-" : r.getGrade(), r.getDiagnostics().size()));
            }
        }
        assertSnapshot("policy-scores.txt", out.toString());
    }

    private static String str(Object value) {
        return value == null ? "~" : value.toString();
    }

    private static String num(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static void assertSnapshot(String name, String actual) {
        Path file = SNAPSHOT_DIR.resolve(name);
        if (UPDATE) {
            try {
                Files.createDirectories(SNAPSHOT_DIR);
                Files.writeString(file, actual);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return;
        }
        String expected;
        try (InputStream in = PolicySnapshotTest.class.getResourceAsStream("/snapshots/" + name)) {
            if (in == null) {
                throw new AssertionError("Missing snapshot " + name
                        + " — regenerate with -Dsnapshot.update=true and commit src/test/resources/snapshots/");
            }
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(expected, actual, name + " changed — review the diff; if intended, regenerate with -Dsnapshot.update=true");
    }
}
