package net.dublinux.arete.validation.policy;

import net.dublinux.arete.validation.policy.PolicyBundleLoader.LoadOptions;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every bundled rule that ships both a {@code Matcher.star} and a
 * {@code Matcher.groovy} must produce identical diagnostics from the
 * (sandboxed) Starlark runtime and the (opt-in) Groovy runtime, so switching
 * {@code rule-languages} never changes a policy's findings. Every bundle
 * rule is exercised against every shared fixture spec with its real parameters.
 *
 * <p>{@link #KNOWN_DIVERGENCES} is the escape hatch for a rule whose two
 * sources are intentionally (or not-yet-reconciled) different; the sweep fails
 * if the real divergence set differs from it in either direction.
 */
class GroovyStarlarkParityTest {

    /** Rules whose Groovy and Starlark sources are knowingly out of sync. */
    private static final Set<String> KNOWN_DIVERGENCES = Set.of();

    private static final ClasspathBundleResources RESOURCES =
            new ClasspathBundleResources(GroovyStarlarkParityTest.class.getClassLoader());
    private static final PolicyBundle STARLARK =
            new PolicyBundleLoader().load(RESOURCES, new LoadOptions(List.of("starlark")));
    private static final PolicyBundle GROOVY =
            new PolicyBundleLoader().load(RESOURCES, new LoadOptions(List.of("groovy", "starlark")));

    private static final Map<String, String> SPECS = Map.of(
            "catalogue", ParityFixtures.CATALOGUE_SPEC,
            "lint", ParityFixtures.LINT_SPEC,
            "house-style", ParityFixtures.HOUSE_STYLE_SPEC,
            "schema", ParityFixtures.SCHEMA_SPEC,
            "ops", ParityFixtures.OPS_SPEC);

    @Test
    void groovyRulesMatchStarlarkAcrossEveryBundleRule() {
        Set<String> divergentRules = new TreeSet<>();
        StringBuilder report = new StringBuilder();
        int compared = 0;

        for (PolicyRule rule : STARLARK.rules().values()) {
            Matcher groovyRule = GROOVY.matchers().get(rule.matcherId());
            if (!"groovy".equals(groovyRule.language())) continue;
            Matcher starlarkRule = STARLARK.matchers().get(rule.matcherId());

            for (Map.Entry<String, String> spec : SPECS.entrySet()) {
                compared++;
                Map<String, Object> api = toMap(spec.getValue());
                Object starlark = runOrError(() -> new StarlarkMatcherEvaluator().execute(starlarkRule, api, rule));
                Object groovy = runOrError(() -> new GroovyMatcherEvaluator().execute(groovyRule, api, rule));
                if (!starlark.equals(groovy)) {
                    divergentRules.add(rule.matcherId());
                    report.append("\n  ").append(rule.id()).append(" (").append(rule.matcherId())
                            .append(") / ").append(spec.getKey())
                            .append("\n    starlark: ").append(starlark)
                            .append("\n    groovy:   ").append(groovy);
                }
            }
        }

        assertTrue(compared > 150, "expected a broad sweep, only compared " + compared + " rule/spec pairs");
        assertEquals(KNOWN_DIVERGENCES, divergentRules,
                "Groovy/Starlark rule parity changed. Divergences:" + report);
    }

    private static Object runOrError(java.util.concurrent.Callable<List<Diagnostic>> call) {
        try {
            return call.call();
        } catch (Exception e) {
            return "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static Map<String, Object> toMap(String spec) {
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(spec, null, new ParseOptions());
        return OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), spec);
    }
}
