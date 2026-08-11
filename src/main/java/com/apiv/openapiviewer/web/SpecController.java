package com.apiv.openapiviewer.web;

import com.apiv.openapiviewer.domain.SpecEntity;
import com.apiv.openapiviewer.service.EndpointGrouper;
import com.apiv.openapiviewer.service.ParsedSpec;
import com.apiv.openapiviewer.service.SpecParserService;
import com.apiv.openapiviewer.service.SpecStorageService;
import com.apiv.openapiviewer.web.dto.SpecSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Controller
public class SpecController {

    private final SpecParserService specParserService;
    private final SpecStorageService specStorageService;

    public SpecController(SpecParserService specParserService, SpecStorageService specStorageService) {
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String q, Model model) {
        populateSidebar(model, q, null);
        return "index";
    }

    @PostMapping("/api/paste")
    public String paste(@RequestParam String specText, Model model) {
        try {
            ParsedSpec parsed = specParserService.parse(specText);
            model.addAttribute("openApi", parsed.openApi());
            model.addAttribute("tagGroups", EndpointGrouper.group(parsed.openApi()));

            if (parsed.openApi() != null) {
                String title = extractTitle(parsed.openApi());
                if (title != null) {
                    SpecEntity saved = specStorageService.saveOrReplace(title, specText);
                    model.addAttribute("parseErrors", parsed.messages());
                    model.addAttribute("specTitle", saved.getTitle());
                    populateSidebar(model, null, saved.getId());
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
        populateSidebar(model, q, entity.getId());
        return "result";
    }

    @PostMapping("/api/specs/{id}/delete")
    public String delete(@PathVariable Long id) {
        specStorageService.deleteById(id);
        return "redirect:/?closedTab=" + id;
    }

    @GetMapping("/api/specs")
    @ResponseBody
    public List<SpecSummary> listSpecs() {
        return toSummaries(specStorageService.findAll(), null);
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

    private static String extractTitle(io.swagger.v3.oas.models.OpenAPI openApi) {
        if (openApi.getInfo() == null || openApi.getInfo().getTitle() == null) {
            return null;
        }
        String title = openApi.getInfo().getTitle().trim();
        return title.isEmpty() ? null : title;
    }

}
