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

    public SpecController(SpecParserService specParserService, SpecStorageService specStorageService,
            PluginValidationService pluginValidationService, SpecFileWatcher specFileWatcher,
            PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService,
            SpecPluginSettingsService specPluginSettingsService, SpecValidationResultService specValidationResultService,
            net.dublinux.arete.web.api.DeploymentMode deploymentMode) {
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
        this.pluginValidationService = pluginValidationService;
        this.specFileWatcher = specFileWatcher;
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
        this.specPluginSettingsService = specPluginSettingsService;
        this.specValidationResultService = specValidationResultService;
        this.deploymentMode = deploymentMode;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        model.addAttribute("specsDir", specFileWatcher.getSpecsHome().toString());
        model.addAttribute("sharedDeployment", deploymentMode.isShared());
        populateSidebar(model, q, null, NamespaceContext.from(request));
        return "index";
    }

    @PostMapping("/api/paste")
    public String paste(@RequestParam String specText,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        parseAndSave(specText, null, NamespaceContext.from(request), model);
        return "result";
    }

    /** Switches the browser's active namespace (a plain label — not auth). Creates it implicitly: a new slug is real once a spec lands in it. */
    @PostMapping("/ui/namespace")
    public String setNamespace(@RequestParam String name,
            jakarta.servlet.http.HttpServletResponse response) {
        String slug = net.dublinux.arete.web.api.Slugs.slugify(name);
        if (slug != null) {
            addUiCookie(response, "arete_namespace", slug);
        }
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
    public String loadFile(@RequestParam String filePath,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        NamespaceContext ctx = NamespaceContext.from(request);
        if (deploymentMode.isShared()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Loading specs from a local path is disabled in shared deployment mode.");
        }
        String trimmedPath = filePath == null ? "" : filePath.trim();
        if (trimmedPath.isEmpty()) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of("A file path is required."));
            populateSidebar(model, null, null);
            return "result";
        }

        Path path;
        try {
            path = Path.of(trimmedPath);
        } catch (InvalidPathException e) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of("'" + trimmedPath + "' isn't a valid file path."));
            populateSidebar(model, null, null);
            return "result";
        }
        if (!path.isAbsolute()) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of(
                    "'" + trimmedPath + "' isn't a full path. Enter the file's complete absolute path, "
                            + "e.g. /home/user/spec.yaml."));
            populateSidebar(model, null, null);
            return "result";
        }

        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of("Couldn't read '" + trimmedPath + "': " + e.getMessage()));
            populateSidebar(model, null, null);
            return "result";
        }

        SpecEntity saved = parseAndSave(content, trimmedPath, ctx, model);
        if (saved != null) {
            specFileWatcher.watch(path);
        }
        return "result";
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
    @GetMapping("/spec/{id}")
    public String open(@PathVariable Long id, @RequestParam(required = false) String q,
            @RequestParam(required = false) String ran, @RequestParam(required = false) List<String> plugin,
            @RequestParam Map<String, String> allParams,
            jakarta.servlet.http.HttpServletRequest request, Model model) {
        SpecEntity entity = specStorageService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec not found"));
        model.addAttribute("specNamespace", entity.getNamespace());
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

        String currentContentHash = SpecValidationResultService.contentHashOf(entity.getRawContent());

        if (ran != null) {
            model.addAttribute("activateScore", true);
            Set<String> checkedPluginIds = plugin == null ? Set.of() : Set.copyOf(plugin);
            List<PluginRunRequest> requests = new ArrayList<>();
            for (SpecValidationPlugin candidate : pluginRegistry.getPlugins()) {
                if (!pluginSettingsService.isEnabled(candidate.getId())) {
                    continue;
                }
                boolean enabledForSpec = checkedPluginIds.contains(candidate.getId());
                Integer ruleSetIndex = parseIndexOrNull(allParams.get("ruleSet_" + candidate.getId()));
                specPluginSettingsService.setSelection(id, candidate.getId(), enabledForSpec, ruleSetIndex);
                if (enabledForSpec) {
                    requests.add(new PluginRunRequest(candidate.getId(),
                            resolveRuleSet(candidate.getId(), allParams.get("ruleSet_" + candidate.getId()))));
                }
            }
            if (requests.isEmpty()) {
                specValidationResultService.deleteForSpec(id);
                model.addAttribute("hasBeenScored", false);
            } else {
                AggregatedValidationResult validation = pluginValidationService.validateMany(entity.getRawContent(), requests);
                List<String> activePluginIds = requests.stream().map(PluginRunRequest::pluginId).toList();
                specValidationResultService.save(id, currentContentHash, validation, activePluginIds);
                populateValidationModel(model, validation, activePluginIds);
            }
        } else {
            model.addAttribute("activateScore", false);
            populateCachedValidation(model, id);
        }

        populateSidebar(model, q, entity.getId(), NamespaceContext.from(request));
        model.addAttribute("pluginChoices", pluginChoices(id, allParams));
        return "result";
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

    /** {@code null} (not {@code 0}) for a blank/unparseable index, so a persisted row can distinguish "never chosen" from "chose index 0". */
    private static Integer parseIndexOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Deleting a spec whose source file is still sitting in a watched folder
     * doesn't really make sense as a permanent removal — the file is the
     * source of truth, so once the DB row is gone this immediately reloads
     * it from disk rather than leaving a confusing gap until the next
     * unrelated filesystem event (or app restart) happens to pick it back up.
     */
    @PostMapping("/api/specs/{id}/delete")
    public String delete(@PathVariable Long id) {
        SpecEntity entity = specStorageService.findById(id).orElse(null);
        specStorageService.deleteById(id);
        specPluginSettingsService.deleteAllForSpec(id);
        specValidationResultService.deleteForSpec(id);
        if (entity != null && entity.getSource() == SpecSource.FILE && entity.getFilePath() != null) {
            Path path = Path.of(entity.getFilePath());
            if (Files.isRegularFile(path)) {
                specFileWatcher.reload(path);
            }
        }
        return "redirect:/?closedTab=" + id;
    }

    /** Polled by the sidebar's client-side refresh so newly-watched/dropped specs appear without a manual reload. */
    @GetMapping("/api/specs")
    @ResponseBody
    public List<SpecSummary> listSpecs(@RequestParam(required = false) String q,
            jakarta.servlet.http.HttpServletRequest request) {
        return toSummaries(specStorageService.findByNamespace(NamespaceContext.from(request).namespace()), q);
    }

    /**
     * Shared parse/save/render-model flow for both entry points.
     * {@code filePath == null} means pasted text; otherwise the content was
     * loaded from that path. Returns the saved entity, or {@code null} if
     * nothing was saved (parse failure, or no title to key it on). Never
     * runs validation itself — that's only ever triggered from the spec
     * view page's Refresh control (see {@link #open}).
     */
    private SpecEntity parseAndSave(String content, String filePath, NamespaceContext ctx, Model model) {
        model.addAttribute("activateScore", false);
        model.addAttribute("hasBeenScored", false);
        try {
            ParsedSpec parsed = specParserService.parse(content);
            model.addAttribute("openApi", parsed.openApi());
            model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));
            model.addAttribute("componentSchemas", componentSchemasOf(parsed.openApi()));
            model.addAttribute("componentRequestBodies", componentRequestBodiesOf(parsed.openApi()));
            model.addAttribute("componentResponses", componentResponsesOf(parsed.openApi()));

            if (parsed.openApi() != null) {
                String title = parsed.title();
                if (title != null) {
                    SpecEntity saved = filePath == null
                            ? specStorageService.saveOrReplace(ctx.namespace(), ctx.submitter(), title, content)
                            : specStorageService.saveOrReplaceFromFile(title, content, filePath);
                    model.addAttribute("parseErrors", parsed.messages());
                    model.addAttribute("specTitle", saved.getTitle());
                    model.addAttribute("specFilePath", saved.getFilePath());
                    populateCachedValidation(model, saved.getId());
                    populateSidebar(model, null, saved.getId(), ctx);
                    return saved;
                } else {
                    model.addAttribute("parseErrors", withWarning(parsed.messages(),
                            "Spec has no 'title' in its info block; it was not saved."));
                    populateSidebar(model, null, null, ctx);
                }
            } else {
                model.addAttribute("parseErrors", parsed.messages());
                populateSidebar(model, null, null, ctx);
            }
        } catch (Exception e) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of("Failed to parse spec: " + e.getMessage()));
            populateSidebar(model, null, null, ctx);
        }
        return null;
    }

    private void populateSidebar(Model model, String q, Long activeId) {
        populateSidebar(model, q, activeId, new NamespaceContext(
                net.dublinux.arete.service.SpecStorageService.DEFAULT_NAMESPACE,
                net.dublinux.arete.service.SpecStorageService.UI_SUBMITTER));
    }

    private void populateSidebar(Model model, String q, Long activeId, NamespaceContext ctx) {
        model.addAttribute("specs", toSummaries(specStorageService.findByNamespace(ctx.namespace()), q));
        model.addAttribute("q", q);
        model.addAttribute("specId", activeId);
        model.addAttribute("namespaces", specStorageService.namespaces());
        model.addAttribute("currentNamespace", ctx.namespace());
        model.addAttribute("currentSubmitter", ctx.submitter());
        model.addAttribute("currentUri", ctx.currentUri());
        model.addAttribute("pluginChoices", pluginChoices(activeId, Map.of()));
    }

    private static List<SpecSummary> toSummaries(List<SpecEntity> entities, String q) {
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return entities.stream()
                .filter(e -> needle == null || needle.isEmpty() || e.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                .map(e -> new SpecSummary(e.getId(), e.getTitle(), e.getUpdatedAt().toEpochMilli()))
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
     * Every globally-enabled plugin, for the view page's plugin picker: its
     * declared rule sets, whether it's checked (its persisted per-spec
     * state, unless overridden below), and which rule set is selected.
     *
     * @param specId          nullable — no spec context (e.g. the sidebar on
     *                        the index page) means every row defaults to
     *                        checked, matching {@link SpecPluginSettingsService}'s
     *                        own no-override-means-enabled default
     * @param ruleSetSelections {@code ruleSet_<pluginId>} request params to
     *                        read the selected index from, keyed exactly as
     *                        the picker form submits them (a just-submitted
     *                        Score); a plugin with no entry here falls
     *                        back to its persisted per-spec choice, then to
     *                        index 0 if that's absent too (or either value
     *                        is out-of-range/non-numeric)
     */
    private List<SpecPluginRunChoice> pluginChoices(Long specId, Map<String, String> ruleSetSelections) {
        List<SpecPluginRunChoice> choices = new ArrayList<>();
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (!pluginSettingsService.isEnabled(plugin.getId())) {
                continue;
            }
            List<String> ruleSets = safeRuleSets(plugin);
            boolean enabledForSpec = specId == null || specPluginSettingsService.isEnabledForSpec(specId, plugin.getId());
            String submitted = ruleSetSelections.get("ruleSet_" + plugin.getId());
            Integer persisted = specId == null ? null : specPluginSettingsService.ruleSetIndexForSpec(specId, plugin.getId());
            int selectedIndex = resolveSelectedIndex(submitted, persisted, ruleSets.size());
            choices.add(new SpecPluginRunChoice(plugin.getId(), plugin.getName(), ruleSets, enabledForSpec, selectedIndex));
        }
        choices.sort(Comparator.comparing(SpecPluginRunChoice::pluginName, String.CASE_INSENSITIVE_ORDER));
        return choices;
    }

    private static int resolveSelectedIndex(String submitted, Integer persisted, int ruleSetCount) {
        Integer index = submitted != null ? parseIndexOrNull(submitted) : persisted;
        return index != null && index >= 0 && index < ruleSetCount ? index : 0;
    }

    /**
     * Defensive: a plugin is untrusted, dynamically loaded code, same as
     * every other call into it (see PluginRegistry, PluginValidationService).
     * Preserves the plugin's own declared order — see {@link
     * SpecValidationPlugin#getRuleSets()} — rather than re-sorting it, since
     * that order is what the picker displays and what {@link
     * #resolveRuleSet} indexes into.
     */
    private List<String> safeRuleSets(SpecValidationPlugin plugin) {
        try {
            return List.copyOf(plugin.getRuleSets());
        } catch (Throwable t) {
            log.warn("Validation plugin '{}' threw from getRuleSets(): {}", plugin.getId(), t.toString());
            return List.of(SpecValidationPlugin.DEFAULT_RULE_SET);
        }
    }

    /**
     * The rule-set picker on the spec view page submits a position in
     * {@code plugin.getRuleSets()} (e.g. {@code "0"}) rather than the rule
     * set's name, so results stay correct even if a plugin's declared
     * rule-set names contain characters that would need care in a URL.
     * Falls back to {@link
     * SpecValidationPlugin#DEFAULT_RULE_SET} for anything that doesn't
     * resolve: an absent/unknown plugin, a non-numeric or out-of-range
     * index — the same "use your own default" fallback {@link
     * SpecValidationPlugin#DEFAULT_RULE_SET}'s own javadoc describes for an
     * unrecognized rule set.
     */
    private String resolveRuleSet(String pluginId, String ruleSetIndex) {
        if (ruleSetIndex == null || ruleSetIndex.isBlank()) {
            return SpecValidationPlugin.DEFAULT_RULE_SET;
        }
        int index;
        try {
            index = Integer.parseInt(ruleSetIndex.trim());
        } catch (NumberFormatException e) {
            return SpecValidationPlugin.DEFAULT_RULE_SET;
        }
        SpecValidationPlugin plugin = findEnabledPlugin(pluginId);
        if (plugin == null) {
            return SpecValidationPlugin.DEFAULT_RULE_SET;
        }
        List<String> ruleSets = safeRuleSets(plugin);
        return index >= 0 && index < ruleSets.size() ? ruleSets.get(index) : SpecValidationPlugin.DEFAULT_RULE_SET;
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
