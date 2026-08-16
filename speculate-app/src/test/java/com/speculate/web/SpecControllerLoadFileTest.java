package com.speculate.web;

import com.speculate.domain.SpecEntity;
import com.speculate.plugin.PluginRegistry;
import com.speculate.plugin.PluginSettingsService;
import com.speculate.plugin.PluginValidationService;
import com.speculate.plugin.SpecPluginSettingsService;
import com.speculate.plugin.SpecValidationResultService;
import com.speculate.service.ParsedSpec;
import com.speculate.service.SpecFileWatcher;
import com.speculate.service.SpecParserService;
import com.speculate.service.SpecStorageService;
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
    private PluginRegistry pluginRegistry;

    @MockitoBean
    private PluginSettingsService pluginSettingsService;

    @MockitoBean
    private SpecPluginSettingsService specPluginSettingsService;

    @MockitoBean
    private SpecValidationResultService specValidationResultService;

    @Test
    void loadingAFileFromAConfirmedAbsolutePathReadsItAndSavesItAsFileSourced(@TempDir Path tempDir) throws Exception {
        Path specFile = tempDir.resolve("spec.yaml");
        Files.writeString(specFile, "openapi: 3.0.0");

        OpenAPI openApi = new OpenAPI().info(new Info().title("Loaded API"));
        when(specParserService.parse("openapi: 3.0.0")).thenReturn(new ParsedSpec(openApi, List.of()));

        SpecEntity saved = new SpecEntity();
        saved.setId(1L);
        saved.setTitle("Loaded API");
        saved.setFilePath(specFile.toString());
        when(specStorageService.saveOrReplaceFromFile(eq("Loaded API"), eq("openapi: 3.0.0"), eq(specFile.toString())))
                .thenReturn(saved);
        when(specStorageService.findAll()).thenReturn(List.of());

        mockMvc.perform(post("/api/load-file").param("filePath", specFile.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Loaded from")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(specFile.toString())));

        verify(specStorageService).saveOrReplaceFromFile(eq("Loaded API"), eq("openapi: 3.0.0"), eq(specFile.toString()));
        verify(specFileWatcher).watch(eq(specFile));
    }

    @Test
    void blankPathIsRejectedWithoutReadingOrSaving() throws Exception {
        when(specStorageService.findAll()).thenReturn(List.of());

        mockMvc.perform(post("/api/load-file").param("filePath", "   "))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("file path is required")));

        verify(specStorageService, never()).saveOrReplaceFromFile(anyString(), anyString(), anyString());
        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

    @Test
    void aBareFilenameIsRejectedAsNotAFullPath() throws Exception {
        when(specStorageService.findAll()).thenReturn(List.of());

        mockMvc.perform(post("/api/load-file").param("filePath", "spec.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("a full path")));

        verify(specStorageService, never()).saveOrReplaceFromFile(anyString(), anyString(), anyString());
        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

    @Test
    void aPathThatDoesNotExistProducesAReadableError(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("does-not-exist.yaml");
        when(specStorageService.findAll()).thenReturn(List.of());

        mockMvc.perform(post("/api/load-file").param("filePath", missing.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Couldn")));

        verify(specStorageService, never()).saveOrReplaceFromFile(anyString(), anyString(), anyString());
        verify(specParserService, never()).parse(any());
        verify(specFileWatcher, never()).watch(any());
    }

}
