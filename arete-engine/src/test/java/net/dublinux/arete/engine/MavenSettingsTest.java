package net.dublinux.arete.engine;

import com.sun.net.httpserver.HttpServer;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** settings.xml decides where maven: policy sources come from and as whom, as it does for the rest of CI. */
class MavenSettingsTest {
    private static final String PATH = "/org/acme/api-policy/2.3.1/api-policy-2.3.1.zip";
    private static final String COORDINATE = "maven:org.acme:api-policy:2.3.1";

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private final List<String> authorisations = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        System.clearProperty("test.secret");
        System.clearProperty("test.profile");
    }

    private String serve(String prefix) throws IOException {
        byte[] bundle = MiniBundle.zip(MiniBundle.base("2.3.1"), "");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) authorisations.add(auth);
            if (exchange.getRequestURI().getPath().equals(prefix + PATH)) {
                exchange.sendResponseHeaders(200, bundle.length);
                exchange.getResponseBody().write(bundle);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Path settings(Path dir, String body) throws IOException {
        Path file = dir.resolve("settings.xml");
        Files.writeString(file, "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\">\n" + body + "\n</settings>\n");
        return file;
    }

    private static Engine engineWith(Path settings, String... profiles) {
        Engine.Builder builder = Engine.builder().policySource(COORDINATE).cacheDir(null).mavenSettings(settings);
        for (String profile : profiles) builder.mavenProfile(profile);
        return builder.build();
    }

    private static boolean scores(Engine engine) {
        return engine.score(SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Mini").build())
                .getStatus() == ScoringResult.Status.SUCCESS;
    }

    @Test
    void aMirrorReplacesCentralAndTheServerSuppliesTheCredentials(@TempDir Path tmp) throws IOException {
        String base = serve("/maven");
        System.setProperty("test.secret", "s3cret");
        Path file = settings(tmp, "<localRepository>" + tmp.resolve("empty-local") + "</localRepository>"
                + "<mirrors><mirror><id>corp</id><mirrorOf>*</mirrorOf><url>" + base + "/maven</url></mirror></mirrors>"
                + "<servers><server><id>corp</id><username>ci</username><password>${test.secret}</password></server></servers>");

        assertTrue(scores(engineWith(file)));

        assertEquals(List.of("/maven" + PATH), requested);
        assertEquals(List.of("Basic " + Base64.getEncoder().encodeToString("ci:s3cret".getBytes())), authorisations);
    }

    @Test
    void aServerCanSupplyAHeaderInsteadOfAPassword(@TempDir Path tmp) throws IOException {
        String base = serve("/maven");
        Path file = settings(tmp, "<localRepository>" + tmp.resolve("empty-local") + "</localRepository>"
                + "<mirrors><mirror><id>corp</id><mirrorOf>*</mirrorOf><url>" + base + "/maven</url></mirror></mirrors>"
                + "<servers><server><id>corp</id><configuration><httpHeaders><property>"
                + "<name>Authorization</name><value>Bearer abc123</value></property></httpHeaders></configuration></server></servers>");

        assertTrue(scores(engineWith(file)));

        assertEquals(List.of("Bearer abc123"), authorisations);
    }

    @Test
    void theRepositoriesOfActiveProfilesAreUsed(@TempDir Path tmp) throws IOException {
        String base = serve("/internal");
        String profile = "<profile><id>internal</id><repositories><repository><id>internal</id><url>" + base + "/internal</url>"
                + "</repository></repositories></profile>";
        String local = "<localRepository>" + tmp.resolve("empty-local") + "</localRepository>";

        // Listed in activeProfiles: used.
        assertTrue(scores(engineWith(settings(tmp, local + "<profiles>" + profile + "</profiles><activeProfiles><activeProfile>internal</activeProfile></activeProfiles>"))));
        // Not active: Maven Central is all that is left, and this test must not reach it.
        BundleValidationException inactive = assertThrows(BundleValidationException.class,
                () -> engineWith(settings(tmp, local + "<profiles>" + profile + "</profiles><mirrors><mirror><id>none</id><mirrorOf>central</mirrorOf><url>"
                        + base + "/nowhere</url></mirror></mirrors>")));
        assertTrue(inactive.getMessage().contains("not found in any configured repository"), inactive.getMessage());
        // Activated the way mvn -P does.
        assertTrue(scores(engineWith(settings(tmp, local + "<profiles>" + profile + "</profiles>"), "internal")));
    }

    @Test
    void aProfileCanBeActiveByDefaultOrBySystemProperty(@TempDir Path tmp) throws IOException {
        String base = serve("/internal");
        String local = "<localRepository>" + tmp.resolve("empty-local") + "</localRepository>";
        String repo = "<repositories><repository><id>internal</id><url>" + base + "/internal</url></repository></repositories>";

        assertTrue(scores(engineWith(settings(tmp, local + "<profiles><profile><id>p</id><activation><activeByDefault>true</activeByDefault></activation>" + repo + "</profile></profiles>"))));

        System.setProperty("test.profile", "yes");
        assertTrue(scores(engineWith(settings(tmp, local + "<profiles><profile><id>p</id><activation><property><name>test.profile</name><value>yes</value></property></activation>" + repo + "</profile></profiles>"))));
    }

    @Test
    void theLocalRepositoryIsTriedFirstSoNoNetworkIsNeeded(@TempDir Path tmp) throws IOException {
        Path artifact = tmp.resolve("local" + PATH);
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, MiniBundle.zip(MiniBundle.base("2.3.1"), ""));

        assertTrue(scores(engineWith(settings(tmp, "<localRepository>" + tmp.resolve("local") + "</localRepository>"))));
        assertEquals(List.of(), requested);
    }

    @Test
    void anEncryptedPasswordOrAMissingVariableIsAClearError(@TempDir Path tmp) throws IOException {
        String base = serve("/maven");
        String mirror = "<localRepository>" + tmp.resolve("empty-local") + "</localRepository><mirrors><mirror><id>corp</id><mirrorOf>*</mirrorOf><url>"
                + base + "/maven</url></mirror></mirrors>";

        BundleValidationException encrypted = assertThrows(BundleValidationException.class, () -> engineWith(settings(tmp,
                mirror + "<servers><server><id>corp</id><username>ci</username><password>{Wm9yS2VuSW5hdGU=}</password></server></servers>")));
        assertTrue(encrypted.getMessage().contains("encrypted"), encrypted.getMessage());

        BundleValidationException missing = assertThrows(BundleValidationException.class, () -> engineWith(settings(tmp,
                mirror + "<servers><server><id>corp</id><username>ci</username><password>${env.ARETE_TEST_SURELY_NOT_SET}</password></server></servers>")));
        assertTrue(missing.getMessage().contains("not set"), missing.getMessage());
    }

    @Test
    void mirrorOfFollowsMavensRules() {
        assertTrue(MavenSettings.matches("*", "any", "https://x.test/r"));
        assertTrue(MavenSettings.matches("central", "central", "https://x.test/r"));
        assertFalse(MavenSettings.matches("central", "other", "https://x.test/r"));
        assertTrue(MavenSettings.matches("external:*", "r", "https://x.test/r"));
        assertFalse(MavenSettings.matches("external:*", "r", "http://localhost:8080/r"));
        assertFalse(MavenSettings.matches("external:*", "r", "file:///repo"));
        assertTrue(MavenSettings.matches("a,b", "b", "https://x.test/r"));
        assertFalse(MavenSettings.matches("*,!internal", "internal", "https://x.test/r"));
        assertTrue(MavenSettings.matches("*,!internal", "central", "https://x.test/r"));
    }

    @Test
    void anActiveProxyIsHonouredExceptForNonProxyHosts(@TempDir Path tmp) throws IOException {
        MavenSettings settings = MavenSettings.load(settings(tmp, "<proxies><proxy><id>corp</id><active>true</active><protocol>http</protocol>"
                + "<host>proxy.corp.test</host><port>3128</port><nonProxyHosts>localhost|*.internal.test</nonProxyHosts></proxy></proxies>"));

        var selector = settings.proxySelector();
        Proxy viaProxy = selector.select(URI.create("https://repo.maven.apache.org/maven2/")).get(0);
        assertEquals(Proxy.Type.HTTP, viaProxy.type());
        assertEquals(new InetSocketAddress("proxy.corp.test", 3128).getPort(), ((InetSocketAddress) viaProxy.address()).getPort());
        assertEquals(Proxy.NO_PROXY, selector.select(URI.create("https://nexus.internal.test/r")).get(0));
        assertEquals(Proxy.NO_PROXY, selector.select(URI.create("http://localhost:8080/r")).get(0));
    }

    @Test
    void anInactiveProxyIsIgnored(@TempDir Path tmp) throws IOException {
        MavenSettings settings = MavenSettings.load(settings(tmp, "<proxies><proxy><id>off</id><active>false</active><host>proxy.corp.test</host><port>3128</port></proxy></proxies>"));
        assertEquals(null, settings.proxySelector());
    }

    @Test
    void aMissingOrUnsafeFileIsRefused(@TempDir Path tmp) throws IOException {
        assertThrows(BundleValidationException.class, () -> MavenSettings.load(tmp.resolve("nope.xml")));
        Path evil = tmp.resolve("evil.xml");
        Files.writeString(evil, "<?xml version=\"1.0\"?><!DOCTYPE settings [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><settings>&x;</settings>");
        assertThrows(BundleValidationException.class, () -> MavenSettings.load(evil));
    }

    @Test
    void placeholdersResolveFromTheEnvironmentAndSystemProperties() {
        Map<String, String> values = new LinkedHashMap<>(Map.of("env.TOKEN", "t0k", "a.b", "c"));
        assertEquals("x-t0k-c-${unknown}", MavenSettings.interpolate("x-${env.TOKEN}-${a.b}-${unknown}", values::get));
    }
}
