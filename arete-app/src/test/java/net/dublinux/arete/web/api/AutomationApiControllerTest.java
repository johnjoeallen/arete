package net.dublinux.arete.web.api;

import net.dublinux.arete.plugin.AggregatedValidationResult;
import net.dublinux.arete.plugin.AttributedDiagnostic;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.plugin.PluginValidationService;
import net.dublinux.arete.plugin.SpecValidationResultService;
import net.dublinux.arete.plugin.ValidationSummary;
import net.dublinux.arete.service.ParsedSpec;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.validation.spi.Diagnostic;
import net.dublinux.arete.validation.spi.Severity;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutomationApiController.class)
class AutomationApiControllerTest {

    private static final String SPEC = "openapi: 3.0.0\ninfo: { title: Widget API, version: 1.0.0 }\npaths: {}\n";

    @Autowired MockMvc mvc;

    @MockitoBean SpecParserService parser;
    @MockitoBean SpecStorageService storage;
    @MockitoBean PluginValidationService validation;
    @MockitoBean PluginRegistry pluginRegistry;
    @MockitoBean PluginSettingsService pluginSettings;
    @MockitoBean SpecValidationResultService results;
    @MockitoBean RemoteSpecFetcher fetcher;
    @MockitoBean DeploymentMode deploymentMode;
    @MockitoBean net.dublinux.arete.service.NamespaceService namespaceService;

    @BeforeEach
    void wire() {
        lenient().when(namespaceService.resolveOrCreate(anyString())).thenAnswer(inv ->
                new net.dublinux.arete.domain.NamespaceEntity(inv.getArgument(0), inv.getArgument(0)));
        lenient().when(namespaceService.findByKey(anyString())).thenAnswer(inv ->
                Optional.of(new net.dublinux.arete.domain.NamespaceEntity(inv.getArgument(0), inv.getArgument(0))));
        lenient().when(namespaceService.list()).thenReturn(List.of(
                new net.dublinux.arete.service.NamespaceService.Namespace("default", "default", 3)));
        OpenAPI openApi = new OpenAPI().info(new Info().title("Widget API").version("1.0.0"));
        lenient().when(parser.parse(anyString())).thenReturn(new ParsedSpec(openApi, List.of()));

        SpecValidationPlugin plugin = org.mockito.Mockito.mock(SpecValidationPlugin.class);
        lenient().when(plugin.getId()).thenReturn("generic-policy");
        lenient().when(plugin.getSuggestedScoreLevel(anyString())).thenReturn(Optional.of("score<90"));
        lenient().when(plugin.getPassingScore(anyString())).thenReturn(java.util.OptionalDouble.of(90.0));
        lenient().when(pluginRegistry.getPlugins()).thenReturn(List.of(plugin));
        lenient().when(pluginSettings.isEnabled("generic-policy")).thenReturn(true);

        lenient().when(storage.saveOrReplace(eq("default"), eq("ci"), eq("Widget API"), anyString()))
                .thenReturn(spec(1L, "default", "ci", "Widget API"));
        lenient().when(storage.findByNamespace(anyString())).thenReturn(List.of());
        lenient().when(storage.findByNamespaceAndSubmitter(anyString(), anyString())).thenReturn(List.of());
        lenient().when(storage.namespaces()).thenReturn(List.of("default", "payments"));
        lenient().when(storage.countInNamespace(anyString())).thenReturn(3L);

        lenient().when(validation.validateOne(anyString(), eq("generic-policy"), anyString()))
                .thenReturn(resultWith(85.0, Severity.WARNING));
        lenient().when(validation.validateMany(anyString(), any()))
                .thenReturn(resultWith(85.0, Severity.WARNING));
        lenient().when(results.findForSpec(any())).thenReturn(Optional.empty());
    }

    @Test
    void submitInlineReturnsVerdictAgainstThePolicyLevel() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs?run=generic-policy/Enterprise Grade")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "ci"))
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdict").value("FAIL"))            // 85 < 90
                .andExpect(jsonPath("$.results[0].validator").value("generic-policy"))
                .andExpect(jsonPath("$.results[0].level.criterion").value("score<90"))
                .andExpect(jsonPath("$.results[0].level.source").value("policy"))
                .andExpect(jsonPath("$.results[0].level.met").value(false))
                .andExpect(jsonPath("$.results[0].grade").value("F"))
                .andExpect(jsonPath("$.results[0].passingScore").value(90.0));
    }

    @Test
    void requestedFailOnOverridesThePolicyLevel() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs?run=generic-policy/x&failOn=error")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "ci"))
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdict").value("PASS"))           // only a WARNING finding
                .andExpect(jsonPath("$.results[0].level.criterion").value("error"))
                .andExpect(jsonPath("$.results[0].level.source").value("request"));
    }

    @Test
    void missingSubmitterIs400() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs?run=generic-policy/x")
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("submitter")));
    }

    @Test
    void missingRunIs400() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "ci"))
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("combination")));
    }

    @Test
    void unknownValidatorIs422() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs?run=nope/x")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "ci"))
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void badNamespaceSlugIs422() throws Exception {
        mvc.perform(get("/api/v1/namespaces/NOT A SLUG/specs"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listsNamespaces() throws Exception {
        mvc.perform(get("/api/v1/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("default"))
                .andExpect(jsonPath("$[0].specCount").value(3));
    }

    @Test
    void httpStatusOnFailFlagChangesTheStatus() throws Exception {
        mvc.perform(post("/api/v1/namespaces/default/specs?run=generic-policy/x&httpStatusOnFail=422")
                        .cookie(new jakarta.servlet.http.Cookie("arete_submitter", "ci"))
                        .contentType("application/yaml").content(SPEC))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.verdict").value("FAIL"));
    }

    // --- fixtures ---

    private static SpecEntity spec(long id, String ns, String submitter, String title) {
        SpecEntity e = new SpecEntity();
        e.setId(id);
        e.setNamespace(ns);
        e.setSubmitter(submitter);
        e.setTitle(title);
        e.setRawContent(SPEC);
        e.setSource(SpecSource.PASTED);
        e.setUpdatedAt(Instant.now());
        return e;
    }

    private static AggregatedValidationResult resultWith(double score, Severity severity) {
        Diagnostic d = Diagnostic.builder().ruleId("REST001").title("t").description("m")
                .severity(severity).pointer("/paths").build();
        return new AggregatedValidationResult(
                List.of(new ValidationSummary("generic-policy", "SUCCESS", 1, null)),
                List.of(new AttributedDiagnostic("generic-policy", "Areté Policy Engine", d)),
                155, score, 100.0, score >= 90 ? "B" : "F", 90.0);
    }
}
