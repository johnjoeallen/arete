package net.dublinux.arete;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole app (so {@code SpecSchemaMigration} actually runs against
 * H2 and every bean wires) and exercises the API pipeline: parse, store, score
 * with the embedded policy engine, and the (namespace, title) uniqueness path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:arete-api-it;DB_CLOSE_DELAY=-1",
        "arete.specs-dir=${java.io.tmpdir}/arete-api-it-specs"
})
class AutomationApiBootTest {

    @Autowired
    MockMvc mvc;

    @Test
    void contextBootsAndNamespaceListingIsEmpty() throws Exception {
        mvc.perform(get("/api/v1/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void submitStoresTheSpecAndScoresItWithTheEmbeddedEngine() throws Exception {
        String ns = "it-" + UUID.randomUUID().toString().substring(0, 8);
        String spec = "openapi: 3.0.0\ninfo: { title: Boot IT API, version: 1.0.0 }\npaths: {}\n";

        mvc.perform(post("/api/v1/namespaces/" + ns + "/specs?run=generic-policy/Zalando")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "boot-it"))
                        .contentType("application/yaml").content(spec))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.results[0].validator").value("generic-policy"))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"));

        // the spec itself was stored
        mvc.perform(get("/api/v1/namespaces/" + ns + "/specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Boot IT API"))
                .andExpect(jsonPath("$[0].submitter").value("boot-it"));
    }

    @Test
    void missingSubmitterIsRejected() throws Exception {
        mvc.perform(post("/api/v1/namespaces/some-ns/specs?run=x/y")
                        .contentType("application/yaml").content("openapi: 3.0.0\ninfo: {title: T, version: 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staticCssIsServedWithAContentHashSoAStaleCacheCannotMaskAReleasedChange() throws Exception {
        String html = mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("href=\"(/css/app-[0-9a-f]{8,}\\.css)\"").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(),
                "expected a fingerprinted /css/app-<hash>.css link, got: " + html.replaceAll("(?s).*?(<link[^>]*app[^>]*>).*", "$1"));

        mvc.perform(get(m.group(1))).andExpect(status().isOk());
    }
}
