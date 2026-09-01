package net.dublinux.arete.web;

import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.service.SpecStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecScoringPlugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginRegistry pluginRegistry;

    @MockitoBean
    private PluginSettingsService pluginSettingsService;

    @MockitoBean
    private SpecStorageService specStorageService;

    @MockitoBean
    private net.dublinux.arete.service.NamespaceService namespaceService;

    @org.junit.jupiter.api.BeforeEach
    void wireNamespaces() {
        org.mockito.Mockito.lenient().when(namespaceService.list()).thenReturn(java.util.List.of(
                new net.dublinux.arete.service.NamespaceService.Namespace("default", "default", 0)));
    }

    @Test
    void listsLoadedPluginsWithTheirPersistedEnabledState() throws Exception {
        when(pluginRegistry.getPlugins()).thenReturn(java.util.List.of(stubPlugin("noop", "Noop Plugin")));
        when(pluginRegistry.getInstallPluginsDir()).thenReturn(Path.of("/opt/arete/plugins"));
        when(pluginRegistry.getUserPluginsDir()).thenReturn(Path.of("/home/user/.arete/plugins"));
        when(pluginSettingsService.isEnabled("noop")).thenReturn(false);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Noop Plugin")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disabled")));
    }

    @Test
    void togglingAPluginFlipsItsPersistedStateAndRedirectsBack() throws Exception {
        when(pluginSettingsService.isEnabled("noop")).thenReturn(true);

        mockMvc.perform(post("/settings/plugins/{id}/toggle", "noop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        verify(pluginSettingsService).setEnabled(eq("noop"), eq(false));
    }

    private static SpecScoringPlugin stubPlugin(String id, String name) {
        return new SpecScoringPlugin() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getVersion() {
                return "1.0";
            }

            @Override
            public Set<SpecFormat> getSupportedFormats() {
                return Set.of(SpecFormat.OPENAPI3);
            }

            @Override
            public void configure(Map<String, String> config) {
            }

            @Override
            public net.dublinux.arete.scoring.spi.ScoringResult score(net.dublinux.arete.scoring.spi.SpecInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
