package net.dublinux.arete.web.api;

import net.dublinux.arete.engine.api.SpecInput;
import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.scoring.AggregatedScoringResult;
import net.dublinux.arete.scoring.AttributedDiagnostic;
import net.dublinux.arete.scoring.EngineRunRequest;
import net.dublinux.arete.scoring.EngineScoringService;
import net.dublinux.arete.scoring.ScoreLevel;
import net.dublinux.arete.scoring.SpecScoringResultService;
import net.dublinux.arete.scoring.ScoringSummary;
import net.dublinux.arete.service.ParsedSpec;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.engine.api.Severity;
import net.dublinux.arete.engine.Engine;
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
    private final EngineScoringService scoring;
    private final Engine engine;
    private final SpecScoringResultService results;
    private final RemoteSpecFetcher fetcher;
    private final DeploymentMode deploymentMode;
    private final net.dublinux.arete.service.NamespaceService namespaces;

    public AutomationApiController(SpecParserService parser, SpecStorageService storage,
            EngineScoringService scoring, Engine engine,
            SpecScoringResultService results,
            RemoteSpecFetcher fetcher, DeploymentMode deploymentMode,
            net.dublinux.arete.service.NamespaceService namespaces) {
        this.parser = parser;
        this.storage = storage;
        this.scoring = scoring;
        this.engine = engine;
        this.results = results;
        this.fetcher = fetcher;
        this.deploymentMode = deploymentMode;
        this.namespaces = namespaces;
    }

    // --- DTOs -----------------------------------------------------------

    public record RunCombination(String validator, String policy) { }

    public record SubmitBody(String url, String spec, List<RunCombination> run) { }

    public record SpecResource(String id, String namespace, String title, String submitter,
            String source, String sourceUrl, String updatedAt, Map<String, String> links) { }

    public record LevelOutcome(String criterion, String source, boolean met) { }

    public record Finding(String ruleId, String severity, String title, String message,
            String pointer, List<String> paths, String documentationUrl) { }

    public record CombinationResult(String validator, String policy, String status, String errorMessage,
            Double score, String grade, Double passingScore, LevelOutcome level, Map<String, Long> counts,
            int rulesEvaluated, List<Finding> findings) { }

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

    @GetMapping("/namespaces/{namespace}/specs/{ref}")
    public SpecResource getSpec(@PathVariable String namespace, @PathVariable String ref) {
        return toResource(require(namespace, ref));
    }

    @GetMapping("/namespaces/{namespace}/specs/{ref}/scoring")
    public ResponseEntity<?> lastScoring(@PathVariable String namespace, @PathVariable String ref,
            @RequestParam(required = false) String format) {
        SpecEntity spec = require(namespace, ref);
        var cached = results.findForSpec(spec.getId());
        if (cached.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "no scoring has been run for spec " + ref);
        }
        AggregatedScoringResult r = cached.get().result();
        List<Finding> findings = findings(r);
        if ("sarif".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(SarifRenderer.render(findings));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("spec", toResource(spec));
        out.put("score", box(r.overallScore()));
        out.put("grade", r.grade());
        out.put("passingScore", box(r.passingScore()));
        out.put("counts", severityCounts(r));
        out.put("findings", findings);
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/namespaces/{namespace}/specs/{ref}")
    public ResponseEntity<Void> deleteSpec(@PathVariable String namespace, @PathVariable String ref) {
        SpecEntity spec = require(namespace, ref);
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

        ScoredRun run = runCombos(rawSpec, combos, failOn);
        persist(saved.getId(), rawSpec, run.forPersistence());

        if ("sarif".equalsIgnoreCase(format)) {
            List<Finding> all = run.results().stream().flatMap(c -> c.findings().stream()).toList();
            return status(run.ok(), httpStatusOnFail, isNew).body(SarifRenderer.render(all));
        }
        return status(run.ok(), httpStatusOnFail, isNew)
                .body(new SubmitResponse(toResource(saved), run.ok(), run.verdict(), run.results()));
    }

    /** Re-score an already-stored spec by its UUID — the flow a CI engine uses after an earlier submit. */
    @PostMapping("/specs/{ref}/score")
    public ResponseEntity<?> rescore(@PathVariable String ref,
            @RequestHeader(name = "Content-Type", required = false) String contentType,
            @RequestParam(name = "run", required = false) List<String> runParams,
            @RequestParam(name = "failOn", defaultValue = "policy") String failOn,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "httpStatusOnFail", required = false) Integer httpStatusOnFail,
            @RequestBody(required = false) byte[] body) {
        SpecEntity spec = storage.findByRef(ref)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "no spec '" + ref + "'"));
        SubmitBody json = body != null && contentType != null && contentType.toLowerCase().contains("json")
                ? parseJson(body) : null;
        List<RunCombination> combos = combinations(runParams, json);
        if (combos.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "name at least one validator/policy combination");
        }
        String rawSpec = spec.getRawContent();
        ScoredRun run = runCombos(rawSpec, combos, failOn);
        persist(spec.getId(), rawSpec, run.forPersistence());
        if ("sarif".equalsIgnoreCase(format)) {
            return status(run.ok(), httpStatusOnFail, false)
                    .body(SarifRenderer.render(run.results().stream().flatMap(c -> c.findings().stream()).toList()));
        }
        return status(run.ok(), httpStatusOnFail, false)
                .body(new SubmitResponse(toResource(spec), run.ok(), run.verdict(), run.results()));
    }

    // --- helpers ------------------------------------------------------

    private record ScoredRun(boolean ok, String verdict, List<CombinationResult> results,
            List<EngineRunRequest> forPersistence) { }

    private ScoredRun runCombos(String rawSpec, List<RunCombination> combos, String failOn) {
        ScoreLevel forcedLevel = "policy".equalsIgnoreCase(failOn) ? null : ScoreLevel.parse(failOn);
        List<CombinationResult> comboResults = new ArrayList<>();
        List<EngineRunRequest> forPersistence = new ArrayList<>();
        boolean ok = true;
        for (RunCombination combo : combos) {
            Engine scorer = enabledEngine(combo.validator());
            if (scorer == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "unknown or disabled validator '" + combo.validator() + "'");
            }
            // Accept a policy slug ("enterprise-grade") or the exact name.
            String policy = net.dublinux.arete.web.Policies.resolve(safePolicies(scorer), combo.policy());
            AggregatedScoringResult r = scoring.scoreOne(rawSpec, scorer.getId(), policy);
            forPersistence.add(new EngineRunRequest(scorer.getId(), policy));

            ResolvedLevel level = resolveLevel(forcedLevel, scorer, policy);
            boolean failed = !statusOf(r).equals("SUCCESS") || level.level().failedBy(r);
            ok &= !failed;
            comboResults.add(new CombinationResult(
                    scorer.getId(), policy, statusOf(r), errorOf(r),
                    box(r.overallScore()), r.grade(), box(r.passingScore()),
                    new LevelOutcome(level.level().describe(), level.source(), !failed),
                    severityCounts(r), Math.max(0, r.rulesEvaluatedCount()), findings(r)));
        }
        return new ScoredRun(ok, ok ? "PASS" : "FAIL", comboResults, forPersistence);
    }

    private void persist(long specId, String rawSpec, List<EngineRunRequest> requests) {
        try {
            AggregatedScoringResult combined = scoring.scoreMany(rawSpec, requests);
            results.save(specId, SpecScoringResultService.contentHashOf(rawSpec),
                    combined, requests.stream().map(EngineRunRequest::engineId).distinct().toList());
        } catch (RuntimeException e) {
            log.warn("Could not persist combined scoring for spec {}: {}", specId, e.toString());
        }
    }

    private List<String> safePolicies(Engine scorer) {
        try {
            return List.copyOf(scorer.getPolicies());
        } catch (Throwable t) {
            return List.of(SpecInput.DEFAULT_POLICY);
        }
    }

    private record ResolvedLevel(ScoreLevel level, String source) { }

    private ResolvedLevel resolveLevel(ScoreLevel forced, Engine scorer, String policy) {
        if (forced != null) {
            return new ResolvedLevel(forced, "request");
        }
        Optional<String> suggested = safeSuggestedLevel(scorer, policy);
        if (suggested.isPresent()) {
            try {
                return new ResolvedLevel(ScoreLevel.parse(suggested.get()), "policy");
            } catch (IllegalArgumentException e) {
                log.warn("Engine '{}' suggested an invalid score level '{}': {}", scorer.getId(), suggested.get(), e.toString());
            }
        }
        return new ResolvedLevel(ScoreLevel.BLOCKER, "default");
    }

    private static Optional<String> safeSuggestedLevel(Engine scorer, String policy) {
        try {
            return scorer.getSuggestedScoreLevel(policy);
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
                        ? new RunCombination(p.trim(), SpecInput.DEFAULT_POLICY)
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

    private SpecEntity require(String namespace, String ref) {
        String key = namespaceKey(namespace);
        return storage.findByRef(ref)
                .filter(s -> s.getNamespace().equals(key))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "no spec '" + ref + "' in namespace '" + namespace + "'"));
    }

    private SubmitBody parseJson(byte[] body) {
        try {
            return Json.MAPPER.readValue(body, SubmitBody.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid JSON body: " + e.getMessage());
        }
    }

    /** Path namespace → its lower-cased key, 404 if it does not exist (reads never auto-create). */
    private String namespaceKey(String namespace) {
        String key = Slugs.require(namespace, "namespace");
        return namespaces.findByKey(key)
                .map(net.dublinux.arete.domain.NamespaceEntity::getNameKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "no namespace '" + namespace + "'"));
    }

    private Engine enabledEngine(String id) {
        if (id == null) {
            return null;
        }
        return Engine.ID.equals(id.trim()) ? engine : null;
    }

    private static ResponseEntity.BodyBuilder status(boolean ok, Integer httpStatusOnFail, boolean isNew) {
        if (!ok && httpStatusOnFail != null) {
            return ResponseEntity.status(httpStatusOnFail);
        }
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK);
    }

    private static SpecResource toResource(SpecEntity s) {
        return new SpecResource(s.getRef(), s.getNamespace(), s.getTitle(), s.getSubmitter(),
                s.getSource().name(), s.getSourceUrl(),
                s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString(),
                Map.of("self", "/api/v1/namespaces/" + s.getNamespace() + "/specs/" + s.getRef(),
                        "score", "/api/v1/specs/" + s.getRef() + "/score",
                        "ui", "/spec/" + s.getRef()));
    }

    private static String statusOf(AggregatedScoringResult r) {
        return r.engineSummaries().stream().findFirst().map(ScoringSummary::status).orElse("ENGINE_ERROR");
    }

    private static String errorOf(AggregatedScoringResult r) {
        return r.engineSummaries().stream().findFirst().map(ScoringSummary::errorMessage).orElse(null);
    }

    private static Map<String, Long> severityCounts(AggregatedScoringResult r) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            counts.put(severity.name().toLowerCase(), 0L);
        }
        for (AttributedDiagnostic d : r.diagnostics()) {
            counts.merge(d.diagnostic().getSeverity().name().toLowerCase(), 1L, Long::sum);
        }
        return counts;
    }

    private static List<Finding> findings(AggregatedScoringResult r) {
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
