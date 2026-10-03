package net.dublinux.arete.engine;

import net.dublinux.arete.engine.api.Diagnostic;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-API specs, scored end to end under every bundled policy.
 *
 * <p>{@code corpus/good/<name>.yaml} is an API a reviewer would accept. It must report nothing,
 * except the rules named in an optional {@code <name>.allowed} file (one rule id per line, then
 * a reason): a documented, deliberate departure, or a conflict between two rules. Every allowed
 * rule must actually fire, so the list cannot go stale.
 *
 * <p>{@code corpus/messy/<name>.yaml} is an API with many problems. Its findings and score under
 * each policy are recorded in {@code <name>.snapshot}; any change is a diff to review.
 *
 * <pre>
 * mvn -pl arete-engine test -Dtest=WholeApiCorpusTest -Dcorpus.update=true
 * mvn -pl arete-engine test -Dtest=WholeApiCorpusTest -Dcorpus.print=true     # show good-spec findings
 * </pre>
 */
class WholeApiCorpusTest {
    private static final boolean UPDATE = Boolean.getBoolean("corpus.update");
    private static final boolean PRINT = Boolean.getBoolean("corpus.print");
    private static final Path ROOT = Path.of("src", "test", "resources", "corpus");

    private static Engine plugin() {
        Engine plugin = new Engine();
        // Bundled policies only, so a developer's ~/.arete/policies cannot change the result.
        plugin.configure(Map.of("policies-dir", "target/no-such-policies-dir"));
        return plugin;
    }

    private static List<String> policies(Engine plugin) {
        List<String> policies = new ArrayList<>(plugin.getPolicies());
        policies.sort(String::compareTo);
        return policies;
    }

    private static List<Path> specs(String dir) throws IOException {
        Path folder = ROOT.resolve(dir);
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(f -> f.getFileName().toString().matches(".+\\.(yaml|yml|json)")).sorted().toList();
        }
    }

    private static String stem(Path spec) {
        String name = spec.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    private static ScoringResult score(Engine plugin, String policy, Path spec) {
        try {
            return plugin.score(SpecInput.builder().content(Files.readString(spec)).format(SpecFormat.OPENAPI3).policy(policy).build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestFactory
    Stream<DynamicTest> goodApisReportNothingBeyondTheirAllowedRules() throws IOException {
        Engine plugin = plugin();
        return specs("good").stream().map(spec -> DynamicTest.dynamicTest(stem(spec), () -> {
            Path allowedFile = spec.resolveSibling(stem(spec) + ".allowed");
            Set<String> allowed = new TreeSet<>();
            if (Files.exists(allowedFile)) {
                for (String line : Files.readAllLines(allowedFile)) {
                    String id = line.strip().split("\\s+")[0];
                    if (!id.isEmpty() && !id.startsWith("#")) allowed.add(id);
                }
            }
            Set<String> fired = new TreeSet<>();
            List<String> unexpected = new ArrayList<>();
            for (String policy : policies(plugin)) {
                ScoringResult result = score(plugin, policy, spec);
                assertEquals(ScoringResult.Status.SUCCESS, result.getStatus(), policy + ": " + result.getErrorMessage());
                for (Diagnostic d : result.getDiagnostics()) {
                    fired.add(d.getRuleId());
                    if (!allowed.contains(d.getRuleId())) unexpected.add(policy + "  " + d.getRuleId() + "  " + d.getPointer() + "  " + d.getDescription());
                }
            }
            if (PRINT) unexpected.forEach(line -> System.out.println("GOOD " + stem(spec) + "  " + line));
            assertEquals(List.of(), unexpected, stem(spec) + " reports rules it should not");
            Set<String> stale = new TreeSet<>(allowed);
            stale.removeAll(fired);
            assertTrue(stale.isEmpty(), stem(spec) + " allows rules that no longer fire: " + stale);
        }));
    }

    @TestFactory
    Stream<DynamicTest> messyApisMatchTheirSnapshots() throws IOException {
        Engine plugin = plugin();
        return specs("messy").stream().map(spec -> DynamicTest.dynamicTest(stem(spec), () -> {
            StringBuilder out = new StringBuilder("# findings and score by policy; regenerate with -Dcorpus.update=true\n");
            for (String policy : policies(plugin)) {
                ScoringResult result = score(plugin, policy, spec);
                assertEquals(ScoringResult.Status.SUCCESS, result.getStatus(), policy + ": " + result.getErrorMessage());
                out.append("\n## ").append(policy).append("  score=").append(result.getOverallScore())
                        .append("  grade=").append(result.getGrade())
                        .append("  findings=").append(result.getDiagnostics().size()).append('\n');
                result.getDiagnostics().stream()
                        .map(d -> "  " + d.getRuleId() + "  " + d.getPointer() + "  |  " + d.getDescription())
                        .sorted().forEach(line -> out.append(line).append('\n'));
            }
            Path snapshot = spec.resolveSibling(stem(spec) + ".snapshot");
            if (UPDATE) Files.writeString(snapshot, out.toString());
            else assertEquals(Files.readString(snapshot), out.toString(), stem(spec) + " changed — review; regenerate with -Dcorpus.update=true");
        }));
    }
}
