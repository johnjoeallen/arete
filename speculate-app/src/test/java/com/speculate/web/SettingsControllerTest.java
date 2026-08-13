package com.speculate.web;

import com.speculate.plugin.PluginRegistry;
import com.speculate.plugin.PluginSettingsService;
import com.speculate.service.SpecStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;

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

    @Test
    void listsLoadedPluginsWithTheirPersistedEnabledState() throws Exception {
        when(pluginRegistry.getPlugins()).thenReturn(java.util.List.of(stubPlugin("noop", "Noop Plugin")));
        when(pluginRegistry.getInstallPluginsDir()).thenReturn(Path.of("/opt/speculate/plugins"));
        when(pluginRegistry.getUserPluginsDir()).thenReturn(Path.of("/home/user/.speculate/plugins"));
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

    private static SpecValidationPlugin stubPlugin(String id, String name) {
        return new SpecValidationPlugin() {
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
            public net.dublinux.speculate.validation.spi.ValidationResult validate(net.dublinux.speculate.validation.spi.SpecInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
