package net.dublinux.arete.web;

import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.plugin.PluginValidationService;
import net.dublinux.arete.plugin.SpecPluginSettingsService;
import net.dublinux.arete.plugin.SpecValidationResultService;
import net.dublinux.arete.service.ParsedSpec;
import net.dublinux.arete.service.SpecFileWatcher;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpecController.class)
class SpecControllerLoadFileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpecParserService specParserService;

    @MockitoBean
    private SpecStorageService specStorageService;

    @MockitoBean
    private PluginValidationService pluginValidationService;

    @MockitoBean
    private SpecFileWatcher specFileWatcher;

    @MockitoBean
    private net.dublinux.arete.web.api.DeploymentMode deploymentMode;

    @MockitoBean
    private PluginRegistry pluginRegistry;

    @MockitoBean
    private PluginSettingsService pluginSettingsService;

    @MockitoBean
    private SpecPluginSettingsService specPluginSettingsService;

    @MockitoBean
    private SpecValidationResultService specValidationResultService;

    @MockitoBean
    private net.dublinux.arete.service.NamespaceService namespaceService;

    @org.junit.jupiter.api.BeforeEach
    void wireNamespaces() {
        org.mockito.Mockito.lenient().when(namespaceService.resolveKey(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new net.dublinux.arete.domain.NamespaceEntity("default", "default"));
        org.mockito.Mockito.lenient().when(namespaceService.findByKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(new net.dublinux.arete.domain.NamespaceEntity("default", "default")));
        org.mockito.Mockito.lenient().when(namespaceService.list()).thenReturn(java.util.List.of());
    }

    @Test
    void loadingAFileFromAConfirmedAbsolutePathReadsItSavesItAndRedirectsToItsRef(@TempDir Path tempDir) throws Exception {
        Path specFile = tempDir.resolve("spec.yaml");
        Files.writeString(specFile, "openapi: 3.0.0");

        OpenAPI openApi = new OpenAPI().info(new Info().title("Loaded API"));
        when(specParserService.parse("openapi: 3.0.0")).thenReturn(new ParsedSpec(openApi, List.of()));

        SpecEntity saved = new SpecEntity();
        saved.setId(1L);
        saved.setRef("abc-123");
        saved.setTitle("Loaded API");
        saved.setFilePath(specFile.toString());
        when(specStorageService.saveOrReplaceFromFile(eq("Loaded API"), eq("openapi: 3.0.0"), eq(specFile.toString())))
                .thenReturn(saved);

        mockMvc.perform(post("/api/load-file").param("filePath", specFile.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/spec/abc-123"));

        verify(specStorageService).saveOrReplaceFromFile(eq("Loaded API"), eq("openapi: 3.0.0"), eq(specFile.toString()));
        verify(specFileWatcher).watch(eq(specFile));
    }

    @Test
    void blankPathRedirectsHomeWithAnError() throws Exception {
        mockMvc.perform(post("/api/load-file").param("filePath", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("saveErrors", org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("file path is required"))));

        verify(specStorageService, never()).saveOrReplaceFromFile(anyString(), anyString(), anyString());
        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

    @Test
    void aBareFilenameRedirectsHomeWithAFullPathError() throws Exception {
        mockMvc.perform(post("/api/load-file").param("filePath", "spec.yaml"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("saveErrors", org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("a full path"))));

        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

    @Test
    void aPathThatDoesNotExistRedirectsHomeWithAReadableError(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("does-not-exist.yaml");

        mockMvc.perform(post("/api/load-file").param("filePath", missing.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("saveErrors", org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Couldn"))));

        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

}
