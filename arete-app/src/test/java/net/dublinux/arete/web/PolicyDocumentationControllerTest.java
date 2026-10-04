package net.dublinux.arete.web;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.service.MarkdownRenderer;
import net.dublinux.arete.engine.api.RuleDocumentation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyDocumentationController.class)
class PolicyDocumentationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private Engine engine;
    @MockitoBean private MarkdownRenderer markdownRenderer;

    @Test
    void rendersDocumentationFromAnEngineAtItsStableUrl() throws Exception {
        documented();
        when(markdownRenderer.render("# REST001\n\nRule text.")).thenReturn("<h1>REST001</h1><p>Rule text.</p>");

        mockMvc.perform(get("/engines/generic-policy/rules/REST001"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rule text.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi-viewer:theme")));
    }

    @Test
    void theOldPluginsUrlStillWorks() throws Exception {
        documented();
        when(markdownRenderer.render("# REST001\n\nRule text.")).thenReturn("<h1>REST001</h1><p>Rule text.</p>");

        mockMvc.perform(get("/plugins/generic-policy/rules/REST001"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rule text.")));
    }

    @Test
    void returnsNotFoundForAnUnknownRule() throws Exception {
        documented();

        mockMvc.perform(get("/engines/generic-policy/rules/MISSING"))
                .andExpect(status().isNotFound());
    }

    private void documented() {
        when(engine.getId()).thenReturn(Engine.ID);
        when(engine.getRuleDocumentation("REST001"))
                .thenReturn(Optional.of(new RuleDocumentation("REST001", "# REST001\n\nRule text.")));
        when(engine.getRuleDocumentation("MISSING")).thenReturn(Optional.empty());
    }
}
