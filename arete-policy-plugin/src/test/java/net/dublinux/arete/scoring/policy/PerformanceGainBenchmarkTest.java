package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecInput;
import net.dublinux.arete.scoring.spi.ScoringResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Quantifies the Distill parse cache: a bundle's matchers are parsed once at
 * load and reused, instead of being reparsed and discarded on every spec
 * scoring. Compares the cached {@code execute(Matcher, ...)} path against the
 * uncached raw {@code execute(String, ...)} path, and reports end-to-end
 * {@code validate()} wall time.
 *
 * <p>Not run in the normal build. Enable with {@code -Darete.benchmark=true}:
 * {@code mvn -pl arete-policy-plugin test -Dtest=PerformanceGainBenchmarkTest -Darete.benchmark=true}.
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
    void reportBreakdown() {
        assumeTrue(ENABLED, "set -Darete.benchmark=true to run");

        PolicyBundle bundle = new PolicyBundleLoader()
                .load(new ClasspathBundleResources(getClass().getClassLoader()));
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();
        String specText = spec(40);

        List<PolicyRule> rules = bundle.policyOrDefault(null).dispositions().keySet().stream()
                .map(id -> bundle.rules().get(id))
                .filter(r -> bundle.matchers().get(r.matcherId()) != null)
                .toList();

        Runnable oaiParse = () -> new OpenAPIV3Parser().readContents(specText, null, new ParseOptions());
        SwaggerParseResult parsed0 = new OpenAPIV3Parser().readContents(specText, null, new ParseOptions());
        Runnable toMap = () -> OpenApiMapAdapter.toMap(parsed0.getOpenAPI(), parsed0.getMessages(), specText);
        Map<String, Object> api0 = OpenApiMapAdapter.toMap(parsed0.getOpenAPI(), parsed0.getMessages(), specText);
        Runnable evalAll = () -> { for (PolicyRule r : rules) distill.execute(bundle.matchers().get(r.matcherId()), api0, r); };

        for (int i = 0; i < 10; i++) { oaiParse.run(); toMap.run(); evalAll.run(); }

        System.out.println("\n=== validate() cost breakdown (40-path spec, " + rules.size() + " rule evals) ===");
        System.out.printf("swagger-parser readContents..... %.2f ms%n", medianOf(oaiParse, 25));
        System.out.printf("OpenApiMapAdapter.toMap........ %.2f ms%n", medianOf(toMap, 25));
        System.out.printf("all matcher evaluations........ %.2f ms  (%.1f us/rule)%n",
                medianOf(evalAll, 25), medianOf(evalAll, 25) * 1000 / rules.size());

        com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        long b0 = tb.getThreadAllocatedBytes(id);
        for (int i = 0; i < 20; i++) evalAll.run();
        long allocated = (tb.getThreadAllocatedBytes(id) - b0) / 20;
        System.out.printf("allocation per full policy pass. %.1f KB  (%.0f bytes/rule)%n",
                allocated / 1024.0, (double) allocated / rules.size());
    }

    /** A schema-heavy spec: {@code n} copies of a property-rich object schema. */
    private static String schemaSpec(int n) {
        StringBuilder b = new StringBuilder();
        b.append("openapi: 3.0.0\n");
        b.append("info: { title: Schema API, version: 1.0.0 }\n");
        b.append("paths: { /ping: { get: { responses: { '200': { description: ok } } } } }\n");
        b.append("components:\n  schemas:\n");
        for (int i = 0; i < n; i++) {
            b.append("    Widget").append(i).append(":\n");
            b.append("      type: object\n");
            b.append("      required: [id, name]\n");
            b.append("      properties:\n");
            b.append("        id: { type: integer }\n");
            b.append("        name: { type: string }\n");
            b.append("        price: { type: number }\n");
            b.append("        count: { type: integer, format: int32 }\n");
            b.append("        tags: { type: array, items: { type: string } }\n");
            b.append("        status: { type: string, enum: [ACTIVE, inactive, 3] }\n");
            b.append("        kind: { type: string, enum: [A, B], x-extensible-enum: [A, B] }\n");
            b.append("        code: { type: string, pattern: '^[A-Z]{3}$', minLength: 3, maxLength: 3 }\n");
            b.append("        ratio: { type: number, minimum: 1, maximum: 10 }\n");
        }
        return b.toString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportGroovyVsDistill() {
        assumeTrue(ENABLED, "set -Darete.benchmark=true to run");

        // The 'schema' matcher: 79 lines of Groovy / 41 of Distill, nested
        // closures over api.schemas[*].properties[*].
        String groovySource = resource("api-policy/matchers/schema/Matcher.groovy");
        String dslSource = resource("api-policy/matchers/schema/Matcher.dsl");
        Matcher groovyMatcher = new Matcher("schema", "groovy", groovySource, List.of("property"), Map.of());
        Matcher dslMatcher = new Matcher("schema", "distill", dslSource, List.of("property"), Map.of());
        PolicyRule rule = new PolicyRule("BENCH", "Bench", "Bench", "schema", "property",
                Map.of("enum-case", "upper-snake-case"), "");

        String specText = schemaSpec(30); // 30 schemas x 9 properties
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(specText, null, new ParseOptions());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), specText);

        GroovyMatcherEvaluator groovy = new GroovyMatcherEvaluator();
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        // Steady-state Groovy: compile the script once, reuse the closure.
        groovy.lang.Closure<Object> groovyClosure =
                (groovy.lang.Closure<Object>) new groovy.lang.GroovyShell().evaluate(groovySource);
        Map<String, Object> ruleMap = rule.asMap();

        // Correctness: all three paths agree.
        List<Diagnostic> viaGroovy = groovy.execute(groovyMatcher, api, rule);
        List<Diagnostic> viaDistill = distill.execute(dslMatcher, api, rule);
        System.out.printf("(groovy findings=%d, distill findings=%d, agree=%b)%n",
                viaGroovy.size(), viaDistill.size(), viaGroovy.equals(viaDistill));
        int findings = viaDistill.size();

        Runnable groovyPerCall = () -> groovy.execute(groovyMatcher, api, rule);   // recompiles every call
        Runnable groovySteady = () -> groovyClosure.call(api, ruleMap);            // compiled once
        Runnable distillCached = () -> distill.execute(dslMatcher, api, rule);     // parsed once (cache)
        Runnable distillRaw = () -> distill.execute(dslSource, api, ruleMap);      // parses every call

        for (int i = 0; i < 50; i++) { groovySteady.run(); distillCached.run(); distillRaw.run(); }
        for (int i = 0; i < 5; i++) groovyPerCall.run();

        System.out.println("\n=== schema matcher: Groovy vs Distill (30 schemas, " + findings + " findings) ===");
        System.out.printf("Groovy, recompile every call.... %8.1f us/call   %8.1f KB/call%n",
                microsOf(groovyPerCall, 40), kbOf(groovyPerCall, 20));
        System.out.printf("Groovy, compiled once........... %8.1f us/call   %8.1f KB/call%n",
                microsOf(groovySteady, 200), kbOf(groovySteady, 100));
        System.out.printf("Distill, raw (parse every call). %8.1f us/call   %8.1f KB/call%n",
                microsOf(distillRaw, 200), kbOf(distillRaw, 100));
        System.out.printf("Distill, cached parse........... %8.1f us/call   %8.1f KB/call%n",
                microsOf(distillCached, 200), kbOf(distillCached, 100));
    }

    private static String resource(String path) {
        try (java.io.InputStream in = PerformanceGainBenchmarkTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static double microsOf(Runnable r, int n) {
        List<Long> t = new ArrayList<>();
        for (int i = 0; i < n; i++) { long s = System.nanoTime(); r.run(); t.add(System.nanoTime() - s); }
        return median(t) / 1_000.0;
    }

    private static double kbOf(Runnable r, int n) {
        com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        long b0 = tb.getThreadAllocatedBytes(id);
        for (int i = 0; i < n; i++) r.run();
        return (tb.getThreadAllocatedBytes(id) - b0) / 1024.0 / n;
    }

    private static double medianOf(Runnable r, int n) {
        List<Long> t = new ArrayList<>();
        for (int i = 0; i < n; i++) { long s = System.nanoTime(); r.run(); t.add(System.nanoTime() - s); }
        return median(t) / 1_000_000.0;
    }

    @Test
    void reportEndToEnd() {
        assumeTrue(ENABLED, "set -Darete.benchmark=true to run");

        PolicyScoringPlugin plugin = new PolicyScoringPlugin();
        plugin.configure(Map.of());
        SpecInput input = SpecInput.builder().content(spec(40)).format(SpecFormat.OPENAPI3).build();

        for (int i = 0; i < 5; i++) plugin.score(input);

        List<Long> runs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long t0 = System.nanoTime();
            ScoringResult r = plugin.score(input);
            runs.add(System.nanoTime() - t0);
            assumeTrue(r.getStatus() == ScoringResult.Status.SUCCESS);
        }
        System.out.println("\n=== end-to-end validate() (distill, in-process, parse cache) ===");
        System.out.printf("median.......................... %.2f ms%n", median(runs) / 1_000_000.0);
    }
}
