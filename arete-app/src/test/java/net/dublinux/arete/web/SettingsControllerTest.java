package net.dublinux.arete.web;

import net.dublinux.arete.service.SpecStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    private SpecStorageService specStorageService;

    @MockitoBean
    private net.dublinux.arete.service.NamespaceService namespaceService;

    @org.junit.jupiter.api.BeforeEach
    void wireNamespaces() {
        org.mockito.Mockito.lenient().when(namespaceService.list()).thenReturn(java.util.List.of(
                new net.dublinux.arete.service.NamespaceService.Namespace("default", "default", 0)));
    }

    @Test
    void theSettingsPageRendersWithItsNamespaces() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("default")));
    }
}
