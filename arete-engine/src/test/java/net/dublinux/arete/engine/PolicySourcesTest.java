package net.dublinux.arete.engine;

import com.sun.net.httpserver.HttpServer;
import net.dublinux.arete.engine.api.Diagnostic;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where policy bundles come from: file, https, a Maven repository, layered; and the checks on them. */
class PolicySourcesTest {
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String serve(Map<String, byte[]> files) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Set<String> rulesFiredBy(Engine engine) {
        ScoringResult result = engine.score(SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Mini").build());
        assertEquals(ScoringResult.Status.SUCCESS, result.getStatus(), result.getErrorMessage());
        Set<String> rules = new TreeSet<>();
        for (Diagnostic d : result.getDiagnostics()) rules.add(d.getRuleId());
        return rules;
    }

    @Test
    void aSourceParsesItsPins() {
        PolicySource source = PolicySource.parse("maven:org.acme:api-policy:2.3.1#sha256=" + "ab".repeat(32) + "&version=2.3.1");
        assertEquals("maven:org.acme:api-policy:2.3.1", source.uri());
        assertEquals("ab".repeat(32), source.sha256());
        assertEquals("2.3.1", source.version());
        assertThrows(BundleValidationException.class, () -> PolicySource.parse("file:x#sha256=nothex"));
        assertThrows(BundleValidationException.class, () -> PolicySource.parse("file:x#colour=red"));
    }

    @Test
    void aDirectoryIsASource(@TempDir Path tmp) {
        MiniBundle.writeDir(tmp.resolve("policy"), MiniBundle.base("1.0.0"));
        Engine engine = Engine.builder().policySource("file:" + tmp.resolve("policy")).cacheDir(null).build();

        assertEquals(Set.of("DOC001"), rulesFiredBy(engine));
        assertEquals(List.of("Mini"), engine.getPolicies());
    }

    @Test
    void aZipIsASourceEvenWithItsFilesInsideOneFolder(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("policy-1.0.0.zip");
        Files.write(zip, MiniBundle.zip(MiniBundle.base("1.0.0"), "policy-1.0.0/"));
        Engine engine = Engine.builder().policySource("file:" + zip).cacheDir(null).build();

        assertEquals(Set.of("DOC001"), rulesFiredBy(engine));
    }

    @Test
    void aFilePinMustMatchTheArchive(@TempDir Path tmp) throws IOException {
        byte[] bytes = MiniBundle.zip(MiniBundle.base("1.0.0"), "");
        Path zip = tmp.resolve("p.zip");
        Files.write(zip, bytes);
        String good = PolicySourceResolver.sha256(bytes);

        Engine.builder().policySource("file:" + zip + "#sha256=" + good).cacheDir(null).build();
        BundleValidationException bad = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource("file:" + zip + "#sha256=" + "0".repeat(64)).cacheDir(null).build());
        assertTrue(bad.getMessage().contains("does not match its pin"), bad.getMessage());
    }

    @Test
    void aBundleIsFetchedOverHttpAndKeptInTheCacheForAnOfflineRun(@TempDir Path tmp) throws IOException {
        byte[] bytes = MiniBundle.zip(MiniBundle.base("1.0.0"), "");
        String digest = PolicySourceResolver.sha256(bytes);
        String base = serve(Map.of("/policy.zip", bytes));
        String source = base + "/policy.zip#sha256=" + digest;

        Engine online = Engine.builder().policySource(source).cacheDir(tmp.resolve("cache")).build();
        assertEquals(Set.of("DOC001"), rulesFiredBy(online));
        assertEquals(1, requests.get());

        server.stop(0);
        Engine offline = Engine.builder().policySource(source).cacheDir(tmp.resolve("cache")).build();
        assertEquals(Set.of("DOC001"), rulesFiredBy(offline));
        assertEquals(1, requests.get(), "the second engine must not touch the network");
    }

    @Test
    void aFetchThatDoesNotMatchItsPinIsRefusedAndNotCached(@TempDir Path tmp) throws IOException {
        String base = serve(Map.of("/policy.zip", MiniBundle.zip(MiniBundle.base("1.0.0"), "")));

        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource(base + "/policy.zip#sha256=" + "1".repeat(64)).cacheDir(tmp.resolve("cache")).build());
        assertTrue(e.getMessage().contains("does not match its pin"), e.getMessage());
        assertTrue(!Files.exists(tmp.resolve("cache")) || Files.list(tmp.resolve("cache")).findAny().isEmpty());
    }

    @Test
    void requiringPinsRefusesAnUnpinnedRemoteSourceBeforeFetching() throws IOException {
        String base = serve(Map.of("/policy.zip", MiniBundle.zip(MiniBundle.base("1.0.0"), "")));

        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource(base + "/policy.zip").requirePin(true).cacheDir(null).build());
        assertTrue(e.getMessage().contains("pin"), e.getMessage());
        assertEquals(0, requests.get());
    }

    @Test
    void plainHttpIsRefusedUnlessItIsLoopback() {
        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource("http://policies.example.test/p.zip").cacheDir(null).build());
        assertTrue(e.getMessage().contains("https"), e.getMessage());
    }

    @Test
    void aMavenCoordinateIsResolvedAgainstARepositoryLayout() throws IOException {
        byte[] bytes = MiniBundle.zip(MiniBundle.base("2.3.1"), "");
        String base = serve(Map.of("/org/acme/api-policy/2.3.1/api-policy-2.3.1.zip", bytes));

        Engine engine = Engine.builder().policySource("maven:org.acme:api-policy:2.3.1")
                .mavenRepository(base + "/").cacheDir(null).build();

        assertEquals(Set.of("DOC001"), rulesFiredBy(engine));
    }

    @Test
    void aMavenCoordinateCanComeFromALocalRepository(@TempDir Path tmp) throws IOException {
        Path artifact = tmp.resolve("repo/org/acme/api-policy/2.3.1/api-policy-2.3.1.zip");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, MiniBundle.zip(MiniBundle.base("2.3.1"), ""));

        Engine engine = Engine.builder().policySource("maven:org.acme:api-policy:2.3.1")
                .mavenRepository("file:" + tmp.resolve("repo")).cacheDir(null).build();

        assertEquals(Set.of("DOC001"), rulesFiredBy(engine));
    }

    @Test
    void aMavenCoordinateNobodyHasIsAClearError() throws IOException {
        String base = serve(Map.of());
        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource("maven:org.acme:nothing:1").mavenRepository(base).cacheDir(null).build());
        assertTrue(e.getMessage().contains("not found in any configured repository"), e.getMessage());
    }

    @Test
    void theVersionPinMustMatchTheBundle(@TempDir Path tmp) {
        MiniBundle.writeDir(tmp.resolve("policy"), MiniBundle.base("1.0.0"));
        Engine.builder().policySource("file:" + tmp.resolve("policy") + "#version=1.0.0").cacheDir(null).build();

        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource("file:" + tmp.resolve("policy") + "#version=2.0.0").cacheDir(null).build());
        assertTrue(e.getMessage().contains("pinned to 2.0.0"), e.getMessage());
    }

    @Test
    void laterSourcesAddToAndReplaceEarlierOnesAndMayUseTheirMatchers(@TempDir Path tmp) {
        MiniBundle.writeDir(tmp.resolve("base"), MiniBundle.base("1.0.0"));
        MiniBundle.writeDir(tmp.resolve("org"), MiniBundle.overlay("1.0.0"));

        Engine engine = Engine.builder().policySource("file:" + tmp.resolve("base")).policySource("file:" + tmp.resolve("org"))
                .cacheDir(null).build();

        // The overlay's Mini replaced the base's and runs DOC015, a rule only the overlay defines.
        assertEquals(Set.of("DOC001", "DOC015"), rulesFiredBy(engine));
    }

    @Test
    void anOverlayThatNamesAnUnknownMatcherFails(@TempDir Path tmp) {
        MiniBundle.writeDir(tmp.resolve("base"), MiniBundle.base("1.0.0"));
        Map<String, String> bad = MiniBundle.overlay("1.0.0");
        bad.put("rules/DOC015.md", bad.get("rules/DOC015.md").replace("matcher: operation", "matcher: nonexistent"));
        MiniBundle.writeDir(tmp.resolve("org"), bad);

        // A rule whose matcher is missing is kept loadable (as a catalogue entry) but cannot be scored.
        Engine engine = Engine.builder().policySource("file:" + tmp.resolve("base")).policySource("file:" + tmp.resolve("org"))
                .cacheDir(null).build();
        ScoringResult result = engine.score(SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Mini").build());
        assertEquals(ScoringResult.Status.PLUGIN_ERROR, result.getStatus());
    }

    @Test
    void anArchiveWithoutAManifestIsRefused(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("p.zip");
        Files.write(zip, MiniBundle.zip(Map.of("README.md", "hello"), ""));
        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource("file:" + zip).cacheDir(null).build());
        assertTrue(e.getMessage().contains("no PolicyBundle.yaml"), e.getMessage());
    }
}
