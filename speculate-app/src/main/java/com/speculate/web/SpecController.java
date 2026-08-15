package com.speculate.web;

import com.speculate.domain.SpecEntity;
import com.speculate.domain.SpecSource;
import com.speculate.plugin.AggregatedValidationResult;
import com.speculate.plugin.ComponentFindings;
import com.speculate.plugin.EndpointFindings;
import com.speculate.plugin.GeneralFindings;
import com.speculate.plugin.PluginRegistry;
import com.speculate.plugin.PluginSettingsService;
import com.speculate.plugin.PluginValidationService;
import com.speculate.service.EndpointGrouper;
import com.speculate.service.ParsedSpec;
import com.speculate.service.SpecFileWatcher;
import com.speculate.service.SpecParserService;
import com.speculate.service.SpecStorageService;
import com.speculate.web.dto.PluginRuleSetChoice;
import com.speculate.web.dto.SpecSummary;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import net.dublinux.speculate.validation.spi.Severity;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
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

@Controller
public class SpecController {

    private static final Logger log = LoggerFactory.getLogger(SpecController.class);

    private final SpecParserService specParserService;
    private final SpecStorageService specStorageService;
    private final PluginValidationService pluginValidationService;
    private final SpecFileWatcher specFileWatcher;
    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettingsService;

    public SpecController(SpecParserService specParserService, SpecStorageService specStorageService,
            PluginValidationService pluginValidationService, SpecFileWatcher specFileWatcher,
            PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService) {
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
        this.pluginValidationService = pluginValidationService;
        this.specFileWatcher = specFileWatcher;
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("specsDir", specFileWatcher.getSpecsHome().toString());
        populateSidebar(model, q, null);
        return "index";
    }

    @PostMapping("/api/paste")
    public String paste(@RequestParam String specText, Model model) {
        parseAndSave(specText, null, model);
        return "result";
    }

    /**
     * Loads a spec directly from the local filesystem, given its full path.
     * The server reads the file itself rather than accepting an upload — a
     * browser can never hand JS a dropped/browsed file's real absolute path
     * (a deliberate File API restriction, not a Speculate limitation), so
     * asking for one via a text field and then also uploading the bytes
     * would just create two client-supplied sources of truth that could
     * disagree. Reading the path directly keeps there being exactly one.
     */
    @PostMapping("/api/load-file")
    public String loadFile(@RequestParam String filePath, Model model) {
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

        SpecEntity saved = parseAndSave(content, trimmedPath, model);
        if (saved != null) {
            specFileWatcher.watch(path);
        }
        return "result";
    }

    /**
     * Renders a spec's docs. Validation is on-demand, not automatic — see
     * {@link PluginValidationService} — so {@code pluginId}/{@code ruleSet}
     * are absent on a plain open (nothing runs, just the picker/Refresh
     * control shows) and present when the Refresh form resubmits here.
     *
     * <p>{@code ruleSet} is the rule set's <em>position</em> in the picker
     * (e.g. {@code "0"}), not its name — see {@link #resolveRuleSet}.
     */
    @GetMapping("/spec/{id}")
    public String open(@PathVariable Long id, @RequestParam(required = false) String q,
            @RequestParam(required = false) String pluginId, @RequestParam(required = false) String ruleSet,
            Model model) {
        SpecEntity entity = specStorageService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec not found"));

        ParsedSpec parsed = specParserService.parse(entity.getRawContent());
        model.addAttribute("openApi", parsed.openApi());
        model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));
        model.addAttribute("componentSchemas", componentSchemasOf(parsed.openApi()));
        model.addAttribute("componentRequestBodies", componentRequestBodiesOf(parsed.openApi()));
        model.addAttribute("componentResponses", componentResponsesOf(parsed.openApi()));
        model.addAttribute("parseErrors", parsed.messages());
        model.addAttribute("specTitle", entity.getTitle());
        model.addAttribute("specFilePath", entity.getFilePath());
        model.addAttribute("selectedPluginId", pluginId);
        model.addAttribute("selectedRuleSet", ruleSet);
        if (pluginId != null && !pluginId.isBlank()) {
            AggregatedValidationResult validation =
                    pluginValidationService.validateOne(entity.getRawContent(), pluginId, resolveRuleSet(pluginId, ruleSet));
            model.addAttribute("validation", validation);
            model.addAttribute("endpointFindings", EndpointFindings.byEndpoint(validation.violations()));
            model.addAttribute("schemaFindings", ComponentFindings.byComponent("schemas", validation.violations()));
            model.addAttribute("requestBodyFindings", ComponentFindings.byComponent("requestBodies", validation.violations()));
            model.addAttribute("responseFindings", ComponentFindings.byComponent("responses", validation.violations()));
            model.addAttribute("generalFindings", GeneralFindings.unattributed(validation.violations()));
            model.addAttribute("severityLabels", severityLabelsOf(pluginId));
        }
        populateSidebar(model, q, entity.getId());
        return "result";
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
    public List<SpecSummary> listSpecs(@RequestParam(required = false) String q) {
        return toSummaries(specStorageService.findAll(), q);
    }

    /**
     * Shared parse/save/render-model flow for both entry points.
     * {@code filePath == null} means pasted text; otherwise the content was
     * loaded from that path. Returns the saved entity, or {@code null} if
     * nothing was saved (parse failure, or no title to key it on). Never
     * runs validation itself — that's only ever triggered from the spec
     * view page's Refresh control (see {@link #open}).
     */
    private SpecEntity parseAndSave(String content, String filePath, Model model) {
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
                            ? specStorageService.saveOrReplace(title, content)
                            : specStorageService.saveOrReplaceFromFile(title, content, filePath);
                    model.addAttribute("parseErrors", parsed.messages());
                    model.addAttribute("specTitle", saved.getTitle());
                    model.addAttribute("specFilePath", saved.getFilePath());
                    populateSidebar(model, null, saved.getId());
                    return saved;
                } else {
                    model.addAttribute("parseErrors", withWarning(parsed.messages(),
                            "Spec has no 'title' in its info block; it was not saved."));
                    populateSidebar(model, null, null);
                }
            } else {
                model.addAttribute("parseErrors", parsed.messages());
                populateSidebar(model, null, null);
            }
        } catch (Exception e) {
            model.addAttribute("openApi", null);
            model.addAttribute("parseErrors", List.of("Failed to parse spec: " + e.getMessage()));
            populateSidebar(model, null, null);
        }
        return null;
    }

    private void populateSidebar(Model model, String q, Long activeId) {
        model.addAttribute("specs", toSummaries(specStorageService.findAll(), q));
        model.addAttribute("q", q);
        model.addAttribute("specId", activeId);
        model.addAttribute("enabledPlugins", enabledPluginChoices());
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

    /** Every enabled plugin with its declared rule sets, for the view page's plugin/rule-set picker. */
    private List<PluginRuleSetChoice> enabledPluginChoices() {
        List<PluginRuleSetChoice> choices = new ArrayList<>();
        for (SpecValidationPlugin plugin : pluginRegistry.getPlugins()) {
            if (!pluginSettingsService.isEnabled(plugin.getId())) {
                continue;
            }
            choices.add(new PluginRuleSetChoice(plugin.getId(), plugin.getName(), safeRuleSets(plugin)));
        }
        choices.sort(Comparator.comparing(PluginRuleSetChoice::pluginName, String.CASE_INSENSITIVE_ORDER));
        return choices;
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
     * Display text for each of the four {@link Severity} levels, as the
     * given plugin would label them (e.g. zally-core's Must/Should/May/Hint)
     * — see {@link SpecValidationPlugin#getSeverityLabel}. Falls back to the
     * SPI's own default labels for an absent/unknown/disabled plugin, or one
     * that throws.
     */
    private Map<String, String> severityLabelsOf(String pluginId) {
        SpecValidationPlugin plugin = findEnabledPlugin(pluginId);
        Map<String, String> labels = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            labels.put(severity.name(), safeSeverityLabel(plugin, severity));
        }
        return labels;
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
