package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A corpus of small APIs per rule: {@code corpus/violations/<RULE>/bad.yaml} breaks the rule,
 * {@code good.yaml} is the same API with only that problem fixed. The rule must report on the
 * bad spec, at the pointers recorded in {@code bad.findings}, and stay silent on the good one.
 *
 * <p>The rule runs on its own, whatever policy would include it, so one rule's behaviour is
 * pinned independently of scoring. After an intended change, regenerate and review the diff:
 * <pre>
 * mvn -pl arete-policy-plugin test -Dtest=RuleCorpusTest -Dcorpus.update=true
 * </pre>
 */
class RuleCorpusTest {
    private static final boolean UPDATE = Boolean.getBoolean("corpus.update");
    private static final Path VIOLATIONS = Path.of("src", "test", "resources", "corpus", "violations");

    @TestFactory
    Stream<DynamicTest> everyRuleReportsItsViolationAndAcceptsItsFix() throws IOException {
        PolicyBundle bundle = new PolicyBundleLoader().load(
                new ClasspathBundleResources(RuleCorpusTest.class.getClassLoader()),
                new PolicyBundleLoader.LoadOptions(List.of("distill")));
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        try (Stream<Path> dirs = Files.list(VIOLATIONS)) {
            return dirs.filter(Files::isDirectory).sorted().toList().stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
                PolicyRule rule = withOverrides(bundle.rules().get(dir.getFileName().toString()), dir);
                assertTrue(rule != null, "no bundled rule " + dir.getFileName());
                Matcher matcher = bundle.matchers().get(rule.matcherId());

                List<String> bad = findings(distill, matcher, rule, dir.resolve("bad.yaml"));
                assertFalse(bad.isEmpty(), rule.id() + " did not report its bad.yaml");
                assertEquals(List.of(), findings(distill, matcher, rule, dir.resolve("good.yaml")),
                        rule.id() + " reported its good.yaml");

                Path recorded = dir.resolve("bad.findings");
                String actual = String.join("\n", bad) + "\n";
                if (UPDATE) Files.writeString(recorded, actual);
                else assertEquals(Files.readString(recorded), actual, rule.id() + " findings changed — review; regenerate with -Dcorpus.update=true");
            }));
        }
    }

    /**
     * Applies {@code parameters.properties}, when a rule's directory has one, over the rule's own
     * parameters: a rule whose default can never be violated by a spec that parses (the supported
     * OpenAPI versions, say) is exercised with the parameter a policy would set.
     */
    private static PolicyRule withOverrides(PolicyRule rule, Path dir) throws IOException {
        Path file = dir.resolve("parameters.properties");
        if (rule == null || !Files.exists(file)) return rule;
        java.util.Properties overrides = new java.util.Properties();
        try (var in = Files.newBufferedReader(file)) { overrides.load(in); }
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<>(rule.parameters());
        for (String key : overrides.stringPropertyNames()) {
            String value = overrides.getProperty(key);
            parameters.put(key, "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : value);
        }
        return new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(), parameters, rule.documentationMarkdown());
    }

    private static List<String> findings(DistillMatcherEvaluator distill, Matcher matcher, PolicyRule rule, Path spec) {
        try {
            String content = Files.readString(spec);
            var parsed = new OpenAPIV3Parser().readContents(content, null, new ParseOptions());
            assertTrue(parsed.getOpenAPI() != null, spec + " does not parse: " + parsed.getMessages());
            var api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), content);
            return distill.execute(matcher, api, rule).stream()
                    .map(d -> d.pointer() + "  |  " + d.message())
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
