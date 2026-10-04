package net.dublinux.arete.gradle;

import net.dublinux.arete.engine.gate.GateJob;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plugin only maps its block onto the engine's job; the mapping is checked here, the behaviour by the end-to-end test. */
class JobConfigTest {
    @Test
    void theBlockMapsOntoTheJob() {
        GateJob.Config config = JobConfig.gate(
                JobConfig.policy("Zalando", List.of("classpath:api-policy", "maven:org.acme:p:1"), List.of("https://repo.example.test/maven"),
                        null, true, true, Path.of("/cache"), Path.of("/policies")),
                Path.of("/repo"), "origin/develop", "abc123", List.of("apis/**/api.yaml"), "https://host/{path}?ref={ref}",
                Map.of("PRIVATE-TOKEN", "t"), Path.of("/out"), true);

        assertEquals("Zalando", config.policy);
        assertEquals(2, config.policySources.size());
        assertEquals(List.of("https://repo.example.test/maven"), config.mavenRepositories);
        assertTrue(config.useDefaultMavenSettings);
        assertTrue(config.requirePin);
        assertEquals(Path.of("/cache"), config.cacheDir);
        assertEquals(Path.of("/policies"), config.userPoliciesDir);
        assertEquals(Path.of("/repo"), config.repository);
        assertEquals("origin/develop", config.target);
        assertEquals("abc123", config.baseSha);
        assertEquals(List.of("apis/**/api.yaml"), config.globs);
        assertEquals("https://host/{path}?ref={ref}", config.rawUrl);
        assertEquals("t", config.rawHeaders.get("PRIVATE-TOKEN"));
        assertEquals(Path.of("/out/gate.md"), config.reportMarkdown);
        assertEquals(Path.of("/out/gate.json"), config.reportJson);
        assertEquals(Path.of("/out/gate.sarif"), config.reportSarif);
        assertTrue(config.reportOnly);
    }

    @Test
    void anExplicitSettingsFileWinsOverTheDefaultLocations() {
        GateJob.Config config = JobConfig.policy(null, List.of(), List.of(), Path.of("/ci/settings.xml"), true, false, null, null);

        assertEquals(List.of(Path.of("/ci/settings.xml")), config.mavenSettingsFiles);
        assertFalse(config.useDefaultMavenSettings);
        assertNull(config.policy);
    }

    @Test
    void rawHeadersAreOnlyKeptInRawMode() {
        GateJob.Config config = JobConfig.gate(JobConfig.policy(null, List.of(), List.of(), null, false, false, null, null),
                Path.of("/repo"), null, null, List.of(), null, Map.of("PRIVATE-TOKEN", "t"), Path.of("/out"), false);

        assertTrue(config.rawHeaders.isEmpty());
        assertNull(config.rawUrl);
    }
}
