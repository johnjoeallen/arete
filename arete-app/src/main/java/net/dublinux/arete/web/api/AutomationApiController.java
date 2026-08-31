package net.dublinux.arete.web.api;

import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.plugin.AggregatedValidationResult;
import net.dublinux.arete.plugin.AttributedDiagnostic;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginRunRequest;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.plugin.PluginValidationService;
import net.dublinux.arete.plugin.ScoreLevel;
import net.dublinux.arete.plugin.SpecValidationResultService;
import net.dublinux.arete.plugin.ValidationSummary;
import net.dublinux.arete.service.ParsedSpec;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.validation.spi.Severity;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The automation API — see {@code design-notes/automation-api.md}. No
 * authentication; namespace and submitter are self-asserted labels, and the
 * deployment MUST sit behind a protected boundary.
 */
@RestController
@RequestMapping("/api/v1")
public class AutomationApiController {

    private static final Logger log = LoggerFactory.getLogger(AutomationApiController.class);

    private final SpecParserService parser;
    private final SpecStorageService storage;
    private final PluginValidationService validation;
    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettings;
    private final SpecValidationResultService results;
    private final RemoteSpecFetcher fetcher;
    private final DeploymentMode deploymentMode;
    private final net.dublinux.arete.service.NamespaceService namespaces;

    public AutomationApiController(SpecParserService parser, SpecStorageService storage,
            PluginValidationService validation, PluginRegistry pluginRegistry,
            PluginSettingsService pluginSettings, SpecValidationResultService results,
            RemoteSpecFetcher fetcher, DeploymentMode deploymentMode,
            net.dublinux.arete.service.NamespaceService namespaces) {
        this.parser = parser;
        this.storage = storage;
        this.validation = validation;
        this.pluginRegistry = pluginRegistry;
        this.pluginSettings = pluginSettings;
        this.results = results;
        this.fetcher = fetcher;
        this.deploymentMode = deploymentMode;
        this.namespaces = namespaces;
    }

    // --- DTOs -----------------------------------------------------------

    public record RunCombination(String validator, String policy) { }

    public record SubmitBody(String url, String spec, List<RunCombination> run) { }

    public record SpecResource(long id, String namespace, String title, String submitter,
            String source, String sourceUrl, String updatedAt, Map<String, String> links) { }

    public record LevelOutcome(String criterion, String source, boolean met) { }

    public record Finding(String ruleId, String severity, String title, String message,
            String pointer, List<String> paths, String documentationUrl) { }

    public record CombinationResult(String validator, String policy, String status, String errorMessage,
            Double score, LevelOutcome level, Map<String, Long> counts, int rulesEvaluated,
            List<Finding> findings) { }

    public record SubmitResponse(SpecResource spec, boolean ok, String verdict, List<CombinationResult> results) { }

    public record NamespaceSummary(String slug, String name, long specCount) { }

    // --- endpoints -----------------------------------------------------

    @GetMapping("/namespaces")
    public List<NamespaceSummary> namespaces() {
        return namespaces.list().stream()
                .map(n -> new NamespaceSummary(n.key(), n.name(), n.specCount()))
                .toList();
    }

    @GetMapping("/namespaces/{namespace}/specs")
    public List<SpecResource> listSpecs(@PathVariable String namespace,
            @RequestParam(required = false) String submitter) {
        String ns = namespaceKey(namespace);
        List<SpecEntity> specs = submitter == null
                ? storage.findByNamespace(ns)
                : storage.findByNamespaceAndSubmitter(ns, Slugs.require(submitter, "submitter"));
        return specs.stream().map(AutomationApiController::toResource).toList();
    }

    @GetMapping("/namespaces/{namespace}/specs/{id}")
    public SpecResource getSpec(@PathVariable String namespace, @PathVariable long id) {
        return toResource(require(namespace, id));
    }

    @GetMapping("/namespaces/{namespace}/specs/{id}/validation")
    public ResponseEntity<?> lastValidation(@PathVariable String namespace, @PathVariable long id,
            @RequestParam(required = false) String format) {
        SpecEntity spec = require(namespace, id);
        var cached = results.findForSpec(spec.getId());
        if (cached.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "no validation has been run for spec " + id);
        }
        AggregatedValidationResult r = cached.get().result();
        List<Finding> findings = findings(r);
        if ("sarif".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(SarifRenderer.render(findings));
        }
        return ResponseEntity.ok(Map.of(
                "spec", toResource(spec),
                "score", box(r.overallScore()),
                "counts", severityCounts(r),
                "findings", findings));
    }

    @DeleteMapping("/namespaces/{namespace}/specs/{id}")
    public ResponseEntity<Void> deleteSpec(@PathVariable String namespace, @PathVariable long id) {
        SpecEntity spec = require(namespace, id);
        storage.deleteById(spec.getId());
        results.deleteForSpec(spec.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/namespaces/{namespace}/specs",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/yaml", "text/yaml",
                    "application/x-yaml", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> submit(
            @PathVariable String namespace,
            @CookieValue(name = "arete_submitter", required = false) String submitterCookie,
            @RequestHeader(name = "X-Arete-Submitter", required = false) String submitterHeader,
            @RequestHeader(name = "Content-Type", required = false) String contentType,
            @RequestParam(name = "run", required = false) List<String> runParams,
            @RequestParam(name = "failOn", defaultValue = "policy") String failOn,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "httpStatusOnFail", required = false) Integer httpStatusOnFail,
            @RequestBody(required = false) byte[] body) {

        String ns = namespaces.resolveOrCreate(namespace).getNameKey();  // creates if new; slugifies the path segment
        String submitter = resolveSubmitter(submitterCookie, submitterHeader);

        SubmitBody json = maybeJson(contentType, body);
        List<RunCombination> combos = combinations(runParams, json);
        if (combos.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "name at least one validator/policy combination via ?run=<validator>/<policy> or a JSON \"run\" array");
        }

        String rawSpec = resolveSpec(contentType, body, json);

        ParsedSpec parsed = parser.parse(rawSpec);
        if (parsed.openApi() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OpenAPI parsing failed: " + String.join("; ", parsed.messages()));
        }
        String title = parsed.title();
        if (title == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "spec has no info.title");
        }

        boolean isNew = storage.findByNamespaceAndSubmitter(ns, submitter).stream()
                .noneMatch(s -> title.equals(s.getTitle()))
                && storage.findByNamespace(ns).stream().noneMatch(s -> title.equals(s.getTitle()));

        SpecEntity saved = json != null && json.url() != null
                ? storage.saveOrReplaceFromUrl(ns, submitter, title, rawSpec, json.url().trim())
                : storage.saveOrReplace(ns, submitter, title, rawSpec);

        ScoreLevel forcedLevel = "policy".equalsIgnoreCase(failOn) ? null : ScoreLevel.parse(failOn);

        List<CombinationResult> comboResults = new ArrayList<>();
        List<PluginRunRequest> forPersistence = new ArrayList<>();
        boolean ok = true;
        for (RunCombination combo : combos) {
            SpecValidationPlugin plugin = enabledPlugin(combo.validator());
            if (plugin == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "unknown or disabled validator '" + combo.validator() + "'");
            }
            String policy = combo.policy() == null || combo.policy().isBlank()
                    ? SpecValidationPlugin.DEFAULT_RULE_SET : combo.policy();
            AggregatedValidationResult r = validation.validateOne(rawSpec, plugin.getId(), policy);
            forPersistence.add(new PluginRunRequest(plugin.getId(), policy));

            ResolvedLevel level = resolveLevel(forcedLevel, plugin, policy);
            boolean failed = statusOf(r).equals("SUCCESS") ? level.level().failedBy(r) : true;
            ok &= !failed;
            comboResults.add(new CombinationResult(
                    plugin.getId(), policy, statusOf(r), errorOf(r),
                    box(r.overallScore()),
                    new LevelOutcome(level.level().describe(), level.source(), !failed),
                    severityCounts(r), Math.max(0, r.rulesEvaluatedCount()), findings(r)));
        }

        // Persist a combined result so the UI's spec view has something to show.
        try {
            AggregatedValidationResult combined = validation.validateMany(rawSpec, forPersistence);
            results.save(saved.getId(), SpecValidationResultService.contentHashOf(rawSpec),
                    combined, forPersistence.stream().map(PluginRunRequest::pluginId).distinct().toList());
        } catch (RuntimeException e) {
            log.warn("Could not persist combined validation for spec {}: {}", saved.getId(), e.toString());
        }

        String verdict = ok ? "PASS" : "FAIL";
        if ("sarif".equalsIgnoreCase(format)) {
            List<Finding> all = comboResults.stream().flatMap(c -> c.findings().stream()).toList();
            return status(ok, httpStatusOnFail, isNew).body(SarifRenderer.render(all));
        }
        SubmitResponse response = new SubmitResponse(toResource(saved), ok, verdict, comboResults);
        return status(ok, httpStatusOnFail, isNew).body(response);
    }

    // --- helpers ------------------------------------------------------

    private record ResolvedLevel(ScoreLevel level, String source) { }

    private ResolvedLevel resolveLevel(ScoreLevel forced, SpecValidationPlugin plugin, String policy) {
        if (forced != null) {
            return new ResolvedLevel(forced, "request");
        }
        Optional<String> suggested = safeSuggestedLevel(plugin, policy);
        if (suggested.isPresent()) {
            try {
                return new ResolvedLevel(ScoreLevel.parse(suggested.get()), "policy");
            } catch (IllegalArgumentException e) {
                log.warn("Plugin '{}' suggested an invalid score level '{}': {}", plugin.getId(), suggested.get(), e.toString());
            }
        }
        return new ResolvedLevel(ScoreLevel.BLOCKER, "default");
    }

    private static Optional<String> safeSuggestedLevel(SpecValidationPlugin plugin, String policy) {
        try {
            return plugin.getSuggestedScoreLevel(policy);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private String resolveSubmitter(String cookie, String header) {
        String raw = cookie != null && !cookie.isBlank() ? cookie : header;
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "no submitter: send the arete_submitter cookie or the X-Arete-Submitter header");
        }
        return Slugs.require(raw, "submitter");
    }

    private SubmitBody maybeJson(String contentType, byte[] body) {
        if (body == null || body.length == 0 || contentType == null
                || !contentType.toLowerCase().contains("json")) {
            return null;
        }
        try {
            Map<?, ?> map = Json.MAPPER.readValue(body, Map.class);
            if (map.containsKey("openapi") || map.containsKey("swagger")) {
                return null; // it's a raw OpenAPI JSON spec, not a structured request
            }
            return Json.MAPPER.convertValue(map, SubmitBody.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid JSON body: " + e.getMessage());
        }
    }

    private List<RunCombination> combinations(List<String> runParams, SubmitBody json) {
        List<RunCombination> out = new ArrayList<>();
        if (runParams != null) {
            for (String p : runParams) {
                int slash = p.indexOf('/');
                out.add(slash < 0
                        ? new RunCombination(p.trim(), SpecValidationPlugin.DEFAULT_RULE_SET)
                        : new RunCombination(p.substring(0, slash).trim(), p.substring(slash + 1).trim()));
            }
        }
        if (json != null && json.run() != null) {
            out.addAll(json.run());
        }
        return out;
    }

    private String resolveSpec(String contentType, byte[] body, SubmitBody json) {
        if (json != null) {
            if (json.url() != null && !json.url().isBlank()) {
                if (deploymentMode.isShared() && json.url().trim().toLowerCase().startsWith("file:")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "file URLs are not allowed in shared mode");
                }
                try {
                    return fetcher.fetch(json.url());
                } catch (RemoteSpecFetcher.FetchException e) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
                }
            }
            if (json.spec() != null && !json.spec().isBlank()) {
                return json.spec();
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "JSON body must contain \"url\" or \"spec\"");
        }
        if (body == null || body.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "empty request body");
        }
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private SpecEntity require(String namespace, long id) {
        return storage.findByIdInNamespace(id, namespaceKey(namespace))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "no spec " + id + " in namespace '" + namespace + "'"));
    }

    /** Path namespace → its lower-cased key, 404 if it does not exist (reads never auto-create). */
    private String namespaceKey(String namespace) {
        String key = Slugs.require(namespace, "namespace");
        return namespaces.findByKey(key)
                .map(net.dublinux.arete.domain.NamespaceEntity::getNameKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "no namespace '" + namespace + "'"));
    }

    private SpecValidationPlugin enabledPlugin(String id) {
        if (id == null) {
            return null;
        }
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (plugin.getId().equals(id.trim()) && pluginSettings.isEnabled(plugin.getId())) {
                return plugin;
            }
        }
        return null;
    }

    private static ResponseEntity.BodyBuilder status(boolean ok, Integer httpStatusOnFail, boolean isNew) {
        if (!ok && httpStatusOnFail != null) {
            return ResponseEntity.status(httpStatusOnFail);
        }
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK);
    }

    private static SpecResource toResource(SpecEntity s) {
        String base = "/api/v1/namespaces/" + s.getNamespace() + "/specs/" + s.getId();
        return new SpecResource(s.getId(), s.getNamespace(), s.getTitle(), s.getSubmitter(),
                s.getSource().name(), s.getSourceUrl(),
                s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString(),
                Map.of("self", base, "ui", "/spec/" + s.getId()));
    }

    private static String statusOf(AggregatedValidationResult r) {
        return r.pluginSummaries().stream().findFirst().map(ValidationSummary::status).orElse("PLUGIN_ERROR");
    }

    private static String errorOf(AggregatedValidationResult r) {
        return r.pluginSummaries().stream().findFirst().map(ValidationSummary::errorMessage).orElse(null);
    }

    private static Map<String, Long> severityCounts(AggregatedValidationResult r) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            counts.put(severity.name().toLowerCase(), 0L);
        }
        for (AttributedDiagnostic d : r.diagnostics()) {
            counts.merge(d.diagnostic().getSeverity().name().toLowerCase(), 1L, Long::sum);
        }
        return counts;
    }

    private static List<Finding> findings(AggregatedValidationResult r) {
        List<Finding> out = new ArrayList<>();
        for (AttributedDiagnostic ad : r.diagnostics()) {
            var d = ad.diagnostic();
            out.add(new Finding(d.getRuleId(), d.getSeverity().name(), d.getTitle(), d.getDescription(),
                    d.getPointer(), d.getPaths(), d.getDocumentationUrl()));
        }
        return out;
    }

    private static Double box(double d) {
        return Double.isNaN(d) ? null : d;
    }

    // --- errors -----------------------------------------------------

    public static final class ApiException extends RuntimeException {
        private final HttpStatus status;

        public ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return problem(e.status, e.getMessage());
    }

    @ExceptionHandler(Slugs.SlugException.class)
    public ResponseEntity<Map<String, Object>> handleSlug(Slugs.SlugException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("title", status.getReasonPhrase());
        body.put("detail", detail);
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
