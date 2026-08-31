package net.dublinux.arete.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import net.dublinux.arete.validation.spi.SpecFormat;
import net.dublinux.arete.validation.spi.SpecInput;
import net.dublinux.arete.validation.spi.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Quantifies the Distill parse cache: a bundle's matchers are parsed once at
 * load and reused, instead of being reparsed and discarded on every spec
 * validation. Compares the cached {@code execute(Matcher, ...)} path against the
 * uncached raw {@code execute(String, ...)} path, and reports end-to-end
 * {@code validate()} wall time.
 *
 * <p>Not run in the normal build. Enable with {@code -Darete.benchmark=true}:
 * {@code mvn -pl policy-based-validation-plugin test -Dtest=PerformanceGainBenchmarkTest -Darete.benchmark=true}.
 */
class PerformanceGainBenchmarkTest {

    private static final boolean ENABLED = Boolean.getBoolean("arete.benchmark");

    /** A synthetic spec sized like a mid-size real API. */
    private static String spec(int paths) {
        StringBuilder b = new StringBuilder();
        b.append("openapi: 3.0.0\n");
        b.append("info: { title: Benchmark API, version: 1.0.0 }\n");
        b.append("servers: [ { url: https://api.example.com/v1 } ]\n");
        b.append("paths:\n");
        for (int i = 0; i < paths; i++) {
            b.append("  /resources").append(i).append("/{id}:\n");
            b.append("    get:\n");
            b.append("      summary: Fetch resource ").append(i).append("\n");
            b.append("      operationId: getResource").append(i).append("\n");
            b.append("      parameters:\n");
            b.append("        - { name: id, in: path, required: true, schema: { type: string } }\n");
            b.append("        - { name: fields, in: query, schema: { type: string } }\n");
            b.append("      responses:\n");
            b.append("        '200':\n");
            b.append("          description: OK\n");
            b.append("          content:\n");
            b.append("            application/json:\n");
            b.append("              schema: { $ref: '#/components/schemas/Resource").append(i).append("' }\n");
            b.append("    put:\n");
            b.append("      summary: Replace resource ").append(i).append("\n");
            b.append("      operationId: putResource").append(i).append("\n");
            b.append("      requestBody:\n");
            b.append("        content: { application/json: { schema: { $ref: '#/components/schemas/Resource").append(i).append("' } } }\n");
            b.append("      responses: { '200': { description: OK } }\n");
        }
        b.append("components:\n  schemas:\n");
        for (int i = 0; i < paths; i++) {
            b.append("    Resource").append(i).append(":\n");
            b.append("      type: object\n");
            b.append("      properties:\n");
            b.append("        id: { type: string }\n");
            b.append("        createdAt: { type: string, format: date-time }\n");
            b.append("        name: { type: string }\n");
            b.append("        count: { type: integer }\n");
        }
        return b.toString();
    }

    private static long median(List<Long> samples) {
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    @Test
    void reportParseCacheGain() {
        assumeTrue(ENABLED, "set -Darete.benchmark=true to run");

        PolicyBundle bundle = new PolicyBundleLoader()
                .load(new ClasspathBundleResources(getClass().getClassLoader()));
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        String specText = spec(40);
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(specText, null, new ParseOptions());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), specText);

        // One (rule -> matcher) pair per rule in the default policy; that is how
        // many matcher evaluations a single validate() performs.
        Policy policy = bundle.policyOrDefault(null);
        List<PolicyRule> rules = policy.dispositions().keySet().stream()
                .map(id -> bundle.rules().get(id))
                .filter(r -> bundle.matchers().get(r.matcherId()) != null)
                .toList();

        // Time-only measurement of parsing every distinct matcher once.
        List<Matcher> distinctMatchers = bundle.matchers().values().stream().toList();
        for (int i = 0; i < 10; i++) {
            DistillMatcherEvaluator warm = new DistillMatcherEvaluator();
            for (Matcher m : distinctMatchers) warm.validate(m);
        }
        List<Long> parseOnly = new ArrayList<>();
        for (int run = 0; run < 50; run++) {
            DistillMatcherEvaluator cold = new DistillMatcherEvaluator();
            long t0 = System.nanoTime();
            for (Matcher m : distinctMatchers) cold.validate(m);
            parseOnly.add(System.nanoTime() - t0);
        }
        double parseAllUs = median(parseOnly) / 1_000.0;
        double perMatcherParseUs = parseAllUs / distinctMatchers.size();

        // Cached vs uncached full evaluation over the whole policy.
        Runnable cached = () -> {
            for (PolicyRule r : rules) distill.execute(bundle.matchers().get(r.matcherId()), api, r);
        };
        Runnable uncached = () -> {
            for (PolicyRule r : rules) {
                Matcher m = bundle.matchers().get(r.matcherId());
                distill.execute(m.source(), api, r.asMap());
            }
        };
        for (int i = 0; i < 10; i++) { cached.run(); uncached.run(); }

        List<Long> cachedRuns = new ArrayList<>();
        List<Long> uncachedRuns = new ArrayList<>();
        for (int run = 0; run < 30; run++) {
            long t0 = System.nanoTime(); cached.run();   long t1 = System.nanoTime();
            uncached.run();                                long t2 = System.nanoTime();
            cachedRuns.add(t1 - t0);
            uncachedRuns.add(t2 - t1);
        }
        double cachedMs = median(cachedRuns) / 1_000_000.0;
        double uncachedMs = median(uncachedRuns) / 1_000_000.0;

        System.out.println("\n=== Distill parse-cache gain ===");
        System.out.printf("distinct bundled matchers............. %d%n", distinctMatchers.size());
        System.out.printf("parse cost........................... %.2f us/matcher%n", perMatcherParseUs);
        System.out.printf("rule evaluations per validate()...... %d%n", rules.size());
        System.out.printf("policy pass, cached parse............ %.3f ms%n", cachedMs);
        System.out.printf("policy pass, reparse every rule...... %.3f ms%n", uncachedMs);
        System.out.printf("=> parse work removed per validate().. ~%.3f ms%n", uncachedMs - cachedMs);
    }

    @Test
    void reportEndToEnd() {
        assumeTrue(ENABLED, "set -Darete.benchmark=true to run");

        PolicyBasedValidationPlugin plugin = new PolicyBasedValidationPlugin();
        plugin.configure(Map.of());
        SpecInput input = SpecInput.builder().content(spec(40)).format(SpecFormat.OPENAPI3).build();

        for (int i = 0; i < 5; i++) plugin.validate(input);

        List<Long> runs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long t0 = System.nanoTime();
            ValidationResult r = plugin.validate(input);
            runs.add(System.nanoTime() - t0);
            assumeTrue(r.getStatus() == ValidationResult.Status.SUCCESS);
        }
        System.out.println("\n=== end-to-end validate() (distill, in-process, parse cache) ===");
        System.out.printf("median.......................... %.2f ms%n", median(runs) / 1_000_000.0);
    }
}
