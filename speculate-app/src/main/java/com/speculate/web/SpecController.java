package com.speculate.web;

import com.speculate.domain.SpecEntity;
import com.speculate.plugin.AggregatedValidationResult;
import com.speculate.plugin.EndpointFindings;
import com.speculate.plugin.PluginValidationService;
import com.speculate.service.EndpointGrouper;
import com.speculate.service.ParsedSpec;
import com.speculate.service.SpecFileWatcher;
import com.speculate.service.SpecParserService;
import com.speculate.service.SpecStorageService;
import com.speculate.web.dto.SpecSummary;
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
import java.util.List;
import java.util.Locale;

@Controller
public class SpecController {

    private final SpecParserService specParserService;
    private final SpecStorageService specStorageService;
    private final PluginValidationService pluginValidationService;
    private final SpecFileWatcher specFileWatcher;

    public SpecController(SpecParserService specParserService, SpecStorageService specStorageService,
            PluginValidationService pluginValidationService, SpecFileWatcher specFileWatcher) {
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
        this.pluginValidationService = pluginValidationService;
        this.specFileWatcher = specFileWatcher;
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

    @GetMapping("/spec/{id}")
    public String open(@PathVariable Long id, @RequestParam(required = false) String q, Model model) {
        SpecEntity entity = specStorageService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spec not found"));

        ParsedSpec parsed = specParserService.parse(entity.getRawContent());
        model.addAttribute("openApi", parsed.openApi());
        model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));
        model.addAttribute("parseErrors", parsed.messages());
        model.addAttribute("specTitle", entity.getTitle());
        model.addAttribute("specFilePath", entity.getFilePath());
        AggregatedValidationResult validation = pluginValidationService.validate(entity.getRawContent());
        model.addAttribute("validation", validation);
        model.addAttribute("endpointFindings", EndpointFindings.byEndpoint(validation.violations()));
        populateSidebar(model, q, entity.getId());
        return "result";
    }

    @PostMapping("/api/specs/{id}/delete")
    public String delete(@PathVariable Long id) {
        specStorageService.deleteById(id);
        return "redirect:/?closedTab=" + id;
    }

    /** Polled by the sidebar's client-side refresh so newly-watched/dropped specs appear without a manual reload. */
    @GetMapping("/api/specs")
    @ResponseBody
    public List<SpecSummary> listSpecs(@RequestParam(required = false) String q) {
        return toSummaries(specStorageService.findAll(), q);
    }

    /**
     * Shared parse/validate/save/render-model flow for both entry points.
     * {@code filePath == null} means pasted text; otherwise the content was
     * loaded from that path. Returns the saved entity, or {@code null} if
     * nothing was saved (parse failure, or no title to key it on).
     */
    private SpecEntity parseAndSave(String content, String filePath, Model model) {
        try {
            ParsedSpec parsed = specParserService.parse(content);
            model.addAttribute("openApi", parsed.openApi());
            model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));

            if (parsed.openApi() != null) {
                String title = parsed.title();
                if (title != null) {
                    SpecEntity saved = filePath == null
                            ? specStorageService.saveOrReplace(title, content)
                            : specStorageService.saveOrReplaceFromFile(title, content, filePath);
                    model.addAttribute("parseErrors", parsed.messages());
                    model.addAttribute("specTitle", saved.getTitle());
                    model.addAttribute("specFilePath", saved.getFilePath());
                    AggregatedValidationResult validation = pluginValidationService.validate(content);
                    model.addAttribute("validation", validation);
                    model.addAttribute("endpointFindings", EndpointFindings.byEndpoint(validation.violations()));
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
    }

    private static List<SpecSummary> toSummaries(List<SpecEntity> entities, String q) {
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return entities.stream()
                .filter(e -> needle == null || needle.isEmpty() || e.getTitle().toLowerCase(Locale.ROOT).contains(needle))
                .map(e -> new SpecSummary(e.getId(), e.getTitle()))
                .sorted(Comparator.comparing(SpecSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<String> withWarning(List<String> messages, String warning) {
        List<String> combined = new ArrayList<>();
        combined.add(warning);
        if (messages != null) {
            combined.addAll(messages);
        }
        return combined;
    }

}
