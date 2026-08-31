package net.dublinux.arete.web;

import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.plugin.AggregatedValidationResult;
import net.dublinux.arete.plugin.CachedValidationResult;
import net.dublinux.arete.plugin.ComponentFindings;
import net.dublinux.arete.plugin.EndpointFindings;
import net.dublinux.arete.plugin.GeneralFindings;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginRunRequest;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.plugin.PluginValidationService;
import net.dublinux.arete.plugin.SpecPluginSettingsService;
import net.dublinux.arete.plugin.SpecValidationResultService;
import net.dublinux.arete.service.EndpointGrouper;
import net.dublinux.arete.service.ParsedSpec;
import net.dublinux.arete.service.SpecFileWatcher;
import net.dublinux.arete.service.SpecParserService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.web.dto.SpecPluginRunChoice;
import net.dublinux.arete.web.dto.SpecSummary;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import net.dublinux.arete.validation.spi.Severity;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
public class SpecController {

    private static final Logger log = LoggerFactory.getLogger(SpecController.class);

    private final SpecParserService specParserService;
    private final SpecStorageService specStorageService;
    private final PluginValidationService pluginValidationService;
    private final SpecFileWatcher specFileWatcher;
    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettingsService;
    private final SpecPluginSettingsService specPluginSettingsService;
    private final SpecValidationResultService specValidationResultService;
    private final net.dublinux.arete.web.api.DeploymentMode deploymentMode;
    private final net.dublinux.arete.service.NamespaceService namespaceService;

    public SpecController(SpecParserService specParserService, SpecStorageService specStorageService,
            PluginValidationService pluginValidationService, SpecFileWatcher specFileWatcher,
            PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService,
            SpecPluginSettingsService specPluginSettingsService, SpecValidationResultService specValidationResultService,
            net.dublinux.arete.web.api.DeploymentMode deploymentMode,
            net.dublinux.arete.service.NamespaceService namespaceService) {
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
        this.pluginValidationService = pluginValidationService;
        this.specFileWatcher = specFileWatcher;
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
        this.specPluginSettingsService = specPluginSettingsService;
        this.specValidationResultService = specValidationResultService;
        this.deploymentMode = deploymentMode;
        this.namespaceService = namespaceService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("specsDir", specFileWatcher.getSpecsHome().toString());
        model.addAttribute("sharedDeployment", deploymentMode.isShared());
        populateSidebar(model, q, (SpecEntity) null, NamespaceContext.from(request));
        return "index";
    }

    @PostMapping("/api/paste")
    public String paste(@RequestParam String specText, jakarta.servlet.http.HttpServletRequest request,
            org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        return afterSave(parseAndSave(specText, null, NamespaceContext.from(request)), flash);
    }

    /** Redirect-after-post: to the spec's own page on success, back to the index with the error otherwise. */
    private static String afterSave(SaveOutcome outcome, org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        if (outcome.saved() != null) {
            return "redirect:/spec/" + outcome.saved().getRef();
        }
        flash.addFlashAttribute("saveErrors", outcome.errors());
        return "redirect:/";
    }

    private record SaveOutcome(SpecEntity saved, List<String> errors) { }

    /** Switches the browser's active namespace by key (create/delete live in Settings). */
    @PostMapping("/ui/namespace")
    public String setNamespace(@RequestParam String key,
            jakarta.servlet.http.HttpServletResponse response) {
        String slug = net.dublinux.arete.web.api.Slugs.slugify(key);
        addUiCookie(response, "arete_namespace",
                slug != null ? slug : net.dublinux.arete.service.NamespaceService.DEFAULT_KEY);
        return "redirect:/";
    }

    /** Sets the browser's submitter label (a plain label — not auth). */
    @PostMapping("/ui/submitter")
    public String setSubmitter(@RequestParam String name,
            @RequestParam(required = false) String returnTo,
            jakarta.servlet.http.HttpServletResponse response) {
        String slug = net.dublinux.arete.web.api.Slugs.slugify(name);
        addUiCookie(response, "arete_submitter",
                slug != null ? slug : net.dublinux.arete.service.SpecStorageService.UI_SUBMITTER);
        return "redirect:" + (returnTo != null && returnTo.startsWith("/") && !returnTo.startsWith("//")
                ? returnTo : "/");
    }

    private static void addUiCookie(jakarta.servlet.http.HttpServletResponse response, String name, String value) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * Loads a spec directly from the local filesystem, given its full path.
     * The server reads the file itself rather than accepting an upload — a
     * browser can never hand JS a dropped/browsed file's real absolute path
     * (a deliberate File API restriction, not an Areté limitation), so
     * asking for one via a text field and then also uploading the bytes
     * would just create two client-supplied sources of truth that could
     * disagree. Reading the path directly keeps there being exactly one.
     */
    @PostMapping("/api/load-file")
    public String loadFile(@RequestParam String filePath, jakarta.servlet.http.HttpServletRequest request,
            org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        NamespaceContext ctx = NamespaceContext.from(request);
        if (deploymentMode.isShared()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Loading specs from a local path is disabled in shared deployment mode.");
        }
        String trimmedPath = filePath == null ? "" : filePath.trim();
        String error = null;
        Path path = null;
        String content = null;
        if (trimmedPath.isEmpty()) {
            error = "A file path is required.";
        } else {
            try {
                path = Path.of(trimmedPath);
                if (!path.isAbsolute()) {
                    error = "'" + trimmedPath + "' isn't a full path. Enter the file's complete absolute path, "
                            + "e.g. /home/user/spec.yaml.";
                } else {
                    content = Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (InvalidPathException e) {
                error = "'" + trimmedPath + "' isn't a valid file path.";
            } catch (IOException e) {
                error = "Couldn't read '" + trimmedPath + "': " + e.getMessage();
            }
        }
        if (error != null) {
            flash.addFlashAttribute("saveErrors", List.of(error));
            return "redirect:/";
        }

        SaveOutcome outcome = parseAndSave(content, trimmedPath, ctx);
        if (outcome.saved() != null) {
            specFileWatcher.watch(path);
        }
        return afterSave(outcome, flash);
    }

    /**
     * Renders a spec's docs. Validation is on-demand, not automatic — see
     * {@link PluginValidationService} — so {@code ran} is absent on a plain
     * open (nothing runs; instead the last Score run's result, if any, is
     * reloaded from {@link SpecValidationResultService} so it doesn't just
     * vanish when the page is left) and present when the Score form
     * resubmits here. {@code plugin} is the checked plugin ids from that
     * form — a plugin present in {@code allParams} (i.e. rendered as a
     * picker row) but absent from {@code plugin} was unchecked, per HTML's
     * normal "an unchecked checkbox submits nothing" behaviour.
     *
     * <p>Every candidate plugin's per-spec enabled state and rule-set choice
     * is persisted from the submitted form before running anything, so both
     * survive a later plain reopen of this page — see {@link
     * #pluginChoices}.
     *
     * <p>Each row's rule set is submitted as {@code ruleSet_<pluginId>},
     * valued by its <em>position</em> in that plugin's rule sets (e.g.
     * {@code "0"}), not its name — see {@link #resolveRuleSet}.
     */
    @GetMapping("/spec/{ref}")
    public String open(@PathVariable String ref, @RequestParam(required = false) String q,
            @RequestParam(required = false) String scored,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        SpecEntity entity = specStorageService.findByRef(ref)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec not found"));
        model.addAttribute("specRef", entity.getRef());
        model.addAttribute("specNamespace",
                namespaceService.findByKey(entity.getNamespace())
                        .map(net.dublinux.arete.domain.NamespaceEntity::getName).orElse(entity.getNamespace()));
        model.addAttribute("specSubmitter", entity.getSubmitter());

        ParsedSpec parsed = specParserService.parse(entity.getRawContent());
        model.addAttribute("openApi", parsed.openApi());
        model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));
        model.addAttribute("componentSchemas", componentSchemasOf(parsed.openApi()));
        model.addAttribute("componentRequestBodies", componentRequestBodiesOf(parsed.openApi()));
        model.addAttribute("componentResponses", componentResponsesOf(parsed.openApi()));
        model.addAttribute("parseErrors", parsed.messages());
        model.addAttribute("specTitle", entity.getTitle());
        model.addAttribute("specFilePath", entity.getFilePath());

        model.addAttribute("activateScore", scored != null);
        populateCachedValidation(model, entity.getId());
        populateSidebar(model, q, entity, NamespaceContext.from(request));
        model.addAttribute("pluginChoices", pluginChoices(entity.getId(), Map.of()));
        return "result";
    }

    /**
     * Runs the picker's chosen validator/rule-set combinations, persists both
     * the choice and the result, and redirects back to the clean spec URL —
     * the run parameters never appear in the address bar.
     */
    @PostMapping("/spec/{ref}/score")
    public String score(@PathVariable String ref, @RequestParam(required = false) List<String> plugin,
            @RequestParam Map<String, String> allParams) {
        SpecEntity entity = specStorageService.findByRef(ref)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec not found"));
        long id = entity.getId();
        Set<String> checkedPluginIds = plugin == null ? Set.of() : Set.copyOf(plugin);
        List<PluginRunRequest> requests = new ArrayList<>();
        for (SpecValidationPlugin candidate : pluginRegistry.getPlugins()) {
            if (!pluginSettingsService.isEnabled(candidate.getId())) {
                continue;
            }
            boolean enabledForSpec = checkedPluginIds.contains(candidate.getId());
            String submitted = allParams.get("ruleSet_" + candidate.getId());
            String ruleSetName = resolveRuleSet(candidate.getId(), submitted);
            specPluginSettingsService.setSelection(id, candidate.getId(), enabledForSpec,
                    ruleSetIndex(candidate.getId(), ruleSetName));
            if (enabledForSpec) {
                requests.add(new PluginRunRequest(candidate.getId(), ruleSetName));
            }
        }
        if (requests.isEmpty()) {
            specValidationResultService.deleteForSpec(id);
        } else {
            AggregatedValidationResult validation = pluginValidationService.validateMany(entity.getRawContent(), requests);
            specValidationResultService.save(id, SpecValidationResultService.contentHashOf(entity.getRawContent()),
                    validation, requests.stream().map(PluginRunRequest::pluginId).toList());
        }
        return "redirect:/spec/" + ref + "?scored";
    }

    /** The position of {@code ruleSetName} in its plugin's rule sets, or null if unknown — for the persisted picker choice. */
    private Integer ruleSetIndex(String pluginId, String ruleSetName) {
        SpecValidationPlugin plugin = findEnabledPlugin(pluginId);
        if (plugin == null) {
            return null;
        }
        int i = safeRuleSets(plugin).indexOf(ruleSetName);
        return i >= 0 ? i : null;
    }

    /**
     * Loads whatever the last Score run for this spec found, if any — used
     * whenever a spec is shown without a fresh run having just happened
     * (a plain reopen, or right after saving a pasted/loaded spec whose
     * title reused an existing row's id).
     */
    private void populateCachedValidation(Model model, Long specId) {
        model.addAttribute("hasBeenScored", false);
        specValidationResultService.findForSpec(specId).ifPresent(cached -> {
            populateValidationModel(model, cached.result(), cached.activePluginIds());
            model.addAttribute("resultFromCache", true);
        });
    }

    private void populateValidationModel(Model model, AggregatedValidationResult validation, List<String> activePluginIds) {
        model.addAttribute("hasBeenScored", true);
        model.addAttribute("validation", validation);
        model.addAttribute("endpointFindings", EndpointFindings.byEndpoint(validation.diagnostics()));
        model.addAttribute("schemaFindings", ComponentFindings.byComponent("schemas", validation.diagnostics()));
        model.addAttribute("requestBodyFindings", ComponentFindings.byComponent("requestBodies", validation.diagnostics()));
        model.addAttribute("responseFindings", ComponentFindings.byComponent("responses", validation.diagnostics()));
        model.addAttribute("generalFindings", GeneralFindings.unattributed(validation.diagnostics()));
        model.addAttribute("severityLabels", severityLabelsOf(activePluginIds));
        model.addAttribute("severityScoreImpact", severityScoreImpactOf(validation));
    }

    /**
     * Deleting a spec whose source file is still sitting in a watched folder
     * doesn't really make sense as a permanent removal — the file is the
     * source of truth, so once the DB row is gone this immediately reloads
     * it from disk rather than leaving a confusing gap.
     */
    @PostMapping("/api/specs/{ref}/delete")
    public String delete(@PathVariable String ref) {
        SpecEntity entity = specStorageService.findByRef(ref).orElse(null);
        if (entity == null) {
            return "redirect:/";
        }
        long id = entity.getId();
        specStorageService.deleteById(id);
        specPluginSettingsService.deleteAllForSpec(id);
        specValidationResultService.deleteForSpec(id);
        if (entity.getSource() == SpecSource.FILE && entity.getFilePath() != null) {
            Path path = Path.of(entity.getFilePath());
            if (Files.isRegularFile(path)) {
                specFileWatcher.reload(path);
            }
        }
        return "redirect:/?closedTab=" + ref;
    }

    /** Polled by the sidebar's client-side refresh so newly-watched/dropped specs appear without a manual reload. */
    @GetMapping("/api/specs")
    @ResponseBody
    public List<SpecSummary> listSpecs(@RequestParam(required = false) String q,
            jakarta.servlet.http.HttpServletRequest request) {
        return toSummaries(specStorageService.findByNamespace(NamespaceContext.from(request).namespace()), q);
    }

    /**
     * Shared parse/save flow for the paste and load-file entry points.
     * {@code filePath == null} means pasted text. Never runs validation
     * itself — scoring is only ever triggered from {@link #score}.
     */
    private SaveOutcome parseAndSave(String content, String filePath, NamespaceContext ctx) {
        ParsedSpec parsed;
        try {
            parsed = specParserService.parse(content);
        } catch (Exception e) {
            return new SaveOutcome(null, List.of("Failed to parse spec: " + e.getMessage()));
        }
        if (parsed.openApi() == null) {
            return new SaveOutcome(null, parsed.messages() == null || parsed.messages().isEmpty()
                    ? List.of("Could not parse this as an OpenAPI/Swagger spec.") : parsed.messages());
        }
        String title = parsed.title();
        if (title == null) {
            return new SaveOutcome(null, withWarning(parsed.messages(),
                    "Spec has no 'title' in its info block; it was not saved."));
        }
        SpecEntity saved = filePath == null
                ? specStorageService.saveOrReplace(namespaceService.resolveKey(ctx.namespace()).getNameKey(),
                        ctx.submitter(), title, content)
                : specStorageService.saveOrReplaceFromFile(title, content, filePath);
        return new SaveOutcome(saved, parsed.messages());
    }

    private void populateSidebar(Model model, String q, SpecEntity active) {
        populateSidebar(model, q, active, new NamespaceContext(
                net.dublinux.arete.service.NamespaceService.DEFAULT_KEY,
                net.dublinux.arete.service.SpecStorageService.UI_SUBMITTER));
    }

    private void populateSidebar(Model model, String q, SpecEntity active, NamespaceContext ctx) {
        net.dublinux.arete.domain.NamespaceEntity current = namespaceService.resolveKey(ctx.namespace());
        model.addAttribute("specs", toSummaries(specStorageService.findByNamespace(current.getNameKey()), q));
        model.addAttribute("q", q);
        model.addAttribute("specId", active == null ? null : active.getRef());
        model.addAttribute("namespaces", namespaceService.list());
        model.addAttribute("currentNamespace", current.getName());
        model.addAttribute("currentNamespaceKey", current.getNameKey());
        model.addAttribute("currentSubmitter", ctx.submitter());
        model.addAttribute("currentUri", ctx.currentUri());
        model.addAttribute("pluginChoices", pluginChoices(active == null ? null : active.getId(), Map.of()));
    }

    private static List<SpecSummary> toSummaries(List<SpecEntity> entities, String q) {
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return entities.stream()
                .filter(e -> needle == null || needle.isEmpty() || e.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                .map(e -> new SpecSummary(e.getRef(), e.getTitle(), e.getUpdatedAt().toEpochMilli()))
                .sorted(Comparator.comparing(SpecSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Never null; empty if the spec declares no {@code components.schemas}. */
    private static Map<String, Schema> componentSchemasOf(OpenAPI openApi) {
        if (openApi == null || openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return Map.of();
        }
        return openApi.getComponents().getSchemas();
    }

    /** Never null; empty if the spec declares no {@code components.requestBodies}. */
    private static Map<String, RequestBody> componentRequestBodiesOf(OpenAPI openApi) {
        if (openApi == null || openApi.getComponents() == null || openApi.getComponents().getRequestBodies() == null) {
            return Map.of();
        }
        return openApi.getComponents().getRequestBodies();
    }

    /** Never null; empty if the spec declares no {@code components.responses}. */
    private static Map<String, ApiResponse> componentResponsesOf(OpenAPI openApi) {
        if (openApi == null || openApi.getComponents() == null || openApi.getComponents().getResponses() == null) {
            return Map.of();
        }
        return openApi.getComponents().getResponses();
    }

    private static List<String> withWarning(List<String> messages, String warning) {
        List<String> combined = new ArrayList<>();
        combined.add(warning);
        if (messages != null) {
            combined.addAll(messages);
        }
        return combined;
    }

    /**
     * Every globally-enabled plugin for the view page's picker: its rule sets
     * (each with a URL-safe slug), whether it's checked for this spec, and the
     * currently selected rule-set slug.
     *
     * @param specId nullable — no spec context (e.g. the index sidebar) means
     *               every row defaults to checked.
     */
    private List<SpecPluginRunChoice> pluginChoices(Long specId, Map<String, String> ignored) {
        List<SpecPluginRunChoice> choices = new ArrayList<>();
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (!pluginSettingsService.isEnabled(plugin.getId())) {
                continue;
            }
            List<String> ruleSets = safeRuleSets(plugin);
            List<SpecPluginRunChoice.RuleSet> options = ruleSets.stream()
                    .map(name -> new SpecPluginRunChoice.RuleSet(name, RuleSets.slug(name)))
                    .toList();
            boolean enabledForSpec = specId == null || specPluginSettingsService.isEnabledForSpec(specId, plugin.getId());
            Integer persistedIndex = specId == null ? null : specPluginSettingsService.ruleSetIndexForSpec(specId, plugin.getId());
            int selected = persistedIndex != null && persistedIndex >= 0 && persistedIndex < ruleSets.size() ? persistedIndex : 0;
            String selectedSlug = ruleSets.isEmpty() ? "" : RuleSets.slug(ruleSets.get(selected));
            choices.add(new SpecPluginRunChoice(plugin.getId(), plugin.getName(), options, enabledForSpec, selectedSlug));
        }
        choices.sort(Comparator.comparing(SpecPluginRunChoice::pluginName, String.CASE_INSENSITIVE_ORDER));
        return choices;
    }

    /** Defensive: a plugin is untrusted, dynamically loaded code. Preserves its declared rule-set order. */
    private List<String> safeRuleSets(SpecValidationPlugin plugin) {
        try {
            return List.copyOf(plugin.getRuleSets());
        } catch (Throwable t) {
            log.warn("Validation plugin '{}' threw from getRuleSets(): {}", plugin.getId(), t.toString());
            return List.of(SpecValidationPlugin.DEFAULT_RULE_SET);
        }
    }

    /** The picker submits {@code ruleSet_<pluginId>} = a rule-set slug; map it back to the plugin's real name. */
    private String resolveRuleSet(String pluginId, String slugOrName) {
        SpecValidationPlugin plugin = findEnabledPlugin(pluginId);
        return plugin == null
                ? SpecValidationPlugin.DEFAULT_RULE_SET
                : RuleSets.resolve(safeRuleSets(plugin), slugOrName);
    }

    private SpecValidationPlugin findEnabledPlugin(String pluginId) {
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (plugin.getId().equals(pluginId) && pluginSettingsService.isEnabled(plugin.getId())) {
                return plugin;
            }
        }
        return null;
    }

    /**
     * Display text for each of the four {@link Severity} levels. With
     * exactly one active plugin, uses that plugin's own vocabulary (e.g.
     * zally-core's Must/Should/May/Hint) — see {@link
     * SpecValidationPlugin#getSeverityLabel}. With zero or several active
     * plugins there's no single vocabulary to prefer (two plugins may label
     * the same {@link Severity} differently), so this falls back to the
     * SPI's own default labels, same as for an absent/unknown/disabled
     * plugin or one that throws.
     */
    private Map<String, String> severityLabelsOf(List<String> activePluginIds) {
        SpecValidationPlugin plugin = activePluginIds.size() == 1 ? findEnabledPlugin(activePluginIds.get(0)) : null;
        Map<String, String> labels = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            labels.put(severity.name(), safeSeverityLabel(plugin, severity));
        }
        return labels;
    }

    /** {@link AggregatedValidationResult#severityScoreImpact()}, keyed by {@link Severity#name()} for template lookup. */
    private static Map<String, Double> severityScoreImpactOf(AggregatedValidationResult validation) {
        Map<String, Double> impact = new LinkedHashMap<>();
        validation.severityScoreImpact().forEach((severity, points) -> impact.put(severity.name(), points));
        return impact;
    }

    private String safeSeverityLabel(SpecValidationPlugin plugin, Severity severity) {
        if (plugin != null) {
            try {
                return plugin.getSeverityLabel(severity);
            } catch (Throwable t) {
                log.warn("Validation plugin '{}' threw from getSeverityLabel({}): {}", plugin.getId(), severity, t.toString());
            }
        }
        return switch (severity) {
            case ERROR -> "Error";
            case WARNING -> "Warning";
            case INFO -> "Info";
            case HINT -> "Hint";
        };
    }

}
