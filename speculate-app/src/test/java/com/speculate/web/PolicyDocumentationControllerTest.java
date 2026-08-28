package com.speculate.web;

import com.speculate.plugin.PluginRegistry;
import com.speculate.service.MarkdownRenderer;
import net.dublinux.speculate.validation.spi.RuleDocumentation;
import net.dublinux.speculate.validation.spi.RuleDocumentationProvider;
import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
import net.dublinux.speculate.validation.spi.ValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyDocumentationController.class)
class PolicyDocumentationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PluginRegistry pluginRegistry;
    @MockitoBean private MarkdownRenderer markdownRenderer;

    @Test
    void rendersDocumentationFromAPluginAtItsStableUrl() throws Exception {
        when(pluginRegistry.getPlugins()).thenReturn(List.of(new DocumentedPlugin()));
        when(markdownRenderer.render("# REST001\n\nRule text.")).thenReturn("<h1>REST001</h1><p>Rule text.</p>");

        mockMvc.perform(get("/plugins/generic-policy/rules/REST001"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rule text.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi-viewer:theme")));
    }

    @Test
    void returnsNotFoundForAnUnknownRule() throws Exception {
        when(pluginRegistry.getPlugins()).thenReturn(List.of(new DocumentedPlugin()));

        mockMvc.perform(get("/plugins/generic-policy/rules/MISSING"))
                .andExpect(status().isNotFound());
    }

    private static final class DocumentedPlugin implements SpecValidationPlugin, RuleDocumentationProvider {
        @Override public String getId() { return "generic-policy"; }
        @Override public String getName() { return "Test"; }
        @Override public String getVersion() { return "1"; }
        @Override public Set<SpecFormat> getSupportedFormats() { return Set.of(SpecFormat.OPENAPI3); }
        @Override public void configure(Map<String, String> config) { }
        @Override public ValidationResult validate(SpecInput input) { return ValidationResult.success(List.of(), 0); }
        @Override public Optional<RuleDocumentation> getRuleDocumentation(String ruleId) {
            return "REST001".equals(ruleId) ? Optional.of(new RuleDocumentation("REST001", "# REST001\n\nRule text.")) : Optional.empty();
        }
    }
}
