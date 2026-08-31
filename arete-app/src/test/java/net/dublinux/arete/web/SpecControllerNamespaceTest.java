package net.dublinux.arete.web;

import net.dublinux.arete.domain.NamespaceEntity;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.plugin.PluginValidationService;
import net.dublinux.arete.plugin.SpecPluginSettingsService;
import net.dublinux.arete.plugin.SpecValidationResultService;
import net.dublinux.arete.service.NamespaceService;
import net.dublinux.arete.service.SpecFileWatcher;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpecController.class)
class SpecControllerNamespaceTest {

    @Autowired MockMvc mvc;

    @MockitoBean SpecParserService specParserService;
    @MockitoBean SpecStorageService specStorageService;
    @MockitoBean PluginValidationService pluginValidationService;
    @MockitoBean SpecFileWatcher specFileWatcher;
    @MockitoBean net.dublinux.arete.web.api.DeploymentMode deploymentMode;
    @MockitoBean PluginRegistry pluginRegistry;
    @MockitoBean PluginSettingsService pluginSettingsService;
    @MockitoBean SpecPluginSettingsService specPluginSettingsService;
    @MockitoBean SpecValidationResultService specValidationResultService;
    @MockitoBean NamespaceService namespaceService;

    @BeforeEach
    void wire() {
        lenient().when(specFileWatcher.getSpecsHome()).thenReturn(java.nio.file.Path.of("/tmp/specs"));
        lenient().when(pluginRegistry.getPlugins()).thenReturn(List.of());
        lenient().when(specStorageService.findByNamespace(anyString())).thenReturn(List.of());
        lenient().when(namespaceService.resolveKey(any())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            return new NamespaceEntity(k == null ? "default" : k, k == null ? "default" : k);
        });
        lenient().when(namespaceService.findByKey(anyString()))
                .thenAnswer(inv -> java.util.Optional.of(new NamespaceEntity(inv.getArgument(0), inv.getArgument(0))));
        lenient().when(namespaceService.list()).thenReturn(List.of(
                new NamespaceService.Namespace("default", "default", 0),
                new NamespaceService.Namespace("Payments Team", "payments-team", 2)));
    }

    @Test
    void setNamespaceSetsTheCookieToTheSelectedKey() throws Exception {
        mvc.perform(post("/ui/namespace").param("key", "payments-team"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().value("arete_namespace", "payments-team"))
                .andExpect(cookie().maxAge("arete_namespace", 60 * 60 * 24 * 365));
    }

    @Test
    void setSubmitterFallsBackToUiForBlank() throws Exception {
        mvc.perform(post("/ui/submitter").param("name", "").param("returnTo", "/spec/7"))
                .andExpect(redirectedUrl("/spec/7"))
                .andExpect(cookie().value("arete_submitter", "ui"));
    }

    @Test
    void setSubmitterRejectsAnOffSiteReturnTo() throws Exception {
        mvc.perform(post("/ui/submitter").param("name", "ci").param("returnTo", "https://evil.example"))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().value("arete_submitter", "ci"));
    }

    @Test
    void sidebarModelCarriesTheNamespaceList() throws Exception {
        mvc.perform(get("/").cookie(new jakarta.servlet.http.Cookie("arete_namespace", "payments-team")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentNamespace", "payments-team"))
                .andExpect(model().attributeExists("namespaces"));
    }
}
