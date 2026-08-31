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
 * H2 and every bean wires) and exercises the API pipeline. No real plugin is
 * on the classpath, so a submit gets as far as "unknown validator" — which
 * still proves parse, store, and the (namespace, title) uniqueness path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:arete-api-it;DB_CLOSE_DELAY=-1",
        "arete.specs-dir=${java.io.tmpdir}/arete-api-it-specs",
        "arete.plugins-dir=${java.io.tmpdir}/arete-api-it-plugins"
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
    void submitStoresTheSpecThenReportsTheUnknownValidator() throws Exception {
        String ns = "it-" + UUID.randomUUID().toString().substring(0, 8);
        String spec = "openapi: 3.0.0\ninfo: { title: Boot IT API, version: 1.0.0 }\npaths: {}\n";

        mvc.perform(post("/api/v1/namespaces/" + ns + "/specs?run=generic-policy/x")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "boot-it"))
                        .contentType("application/yaml").content(spec))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("validator")));

        // the spec itself was stored before validation was attempted
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
}
