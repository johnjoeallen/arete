package net.dublinux.arete.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.web.dto.SpecSummary;
import net.dublinux.arete.validation.spi.MatcherTestProvider;
import net.dublinux.arete.validation.spi.MatcherTestRequest;
import net.dublinux.arete.validation.spi.ValidationResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.Map;

@Controller
public class MatcherTestController {
    private final PluginRegistry pluginRegistry;
    private final SpecStorageService specStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatcherTestController(PluginRegistry pluginRegistry, SpecStorageService specStorageService) {
        this.pluginRegistry = pluginRegistry;
        this.specStorageService = specStorageService;
    }

    @GetMapping("/matcher-test")
    public String page(Model model) {
        populate(model);
        return "matcher-test";
    }

    @PostMapping("/matcher-test")
    public String test(@RequestParam String language, @RequestParam String matcherId,
            @RequestParam String scope, @RequestParam String parameters,
            @RequestParam String matcherSource, @RequestParam String spec, Model model) {
        populate(model);
        model.addAttribute("language", language);
        model.addAttribute("matcherId", matcherId);
        model.addAttribute("scope", scope);
        model.addAttribute("parameters", parameters);
        model.addAttribute("matcherSource", matcherSource);
        model.addAttribute("spec", spec);

        Map<String, Object> parsedParameters;
        try {
            parsedParameters = parameters == null || parameters.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(parameters, new TypeReference<>() {});
        } catch (Exception e) {
            model.addAttribute("testError", "Parameters must be a JSON object: " + e.getMessage());
            return "matcher-test";
        }

        MatcherTestProvider provider = pluginRegistry.getPlugins().stream()
                .filter(MatcherTestProvider.class::isInstance)
                .map(MatcherTestProvider.class::cast)
                .findFirst().orElse(null);
        if (provider == null) {
            model.addAttribute("testError", "No loaded plugin provides matcher testing.");
            return "matcher-test";
        }

        try {
            ValidationResult result = provider.testMatcher(new MatcherTestRequest(language, matcherId, matcherSource,
                    scope, parsedParameters, spec));
            if (result.getStatus() == ValidationResult.Status.SUCCESS) {
                model.addAttribute("diagnostics", result.getDiagnostics());
            } else {
                model.addAttribute("testError", result.getErrorMessage());
            }
        } catch (Throwable e) {
            model.addAttribute("testError", "Matcher test failed: " + e.getMessage());
        }
        return "matcher-test";
    }

    private void populate(Model model) {
        model.addAttribute("specs", specStorageService.findAll().stream()
                .map(e -> new SpecSummary(e.getId(), e.getTitle(), e.getUpdatedAt().toEpochMilli()))
                .sorted(Comparator.comparing(SpecSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList());
        model.addAttribute("q", null);
        model.addAttribute("language", "distill");
        model.addAttribute("matcherId", "my-matcher");
        model.addAttribute("scope", "operation");
        model.addAttribute("parameters", "{}");
        model.addAttribute("matcherSource", """
                distill(api, rule) {
                    return api.paths
                        .expand { path ->
                            path.operationDetails
                            .filter { operation -> operation.summary is blank }
                            .map { operation ->
                                occurrence(
                                    operation.pointer,
                                    operation.method + " " + path.path,
                                    "Operation summary is missing")
                            }
                        };
                }
                """);
        model.addAttribute("spec", "openapi: 3.0.0\ninfo:\n  title: Library API\n  version: 1.2.0\n  description: A small API for browsing and managing books.\nservers:\n  - url: https://api.example.com/v1\npaths:\n  /books:\n    get:\n      summary: List books\n      parameters:\n        - name: limit\n          in: query\n          description: Maximum number of books to return.\n          schema: { type: integer, minimum: 1, maximum: 100, default: 20 }\n      responses:\n        '200':\n          description: A page of books.\n          content:\n            application/json:\n              schema: { type: array, items: { $ref: '#/components/schemas/Book' } }\n    post:\n      requestBody:\n        required: true\n        content:\n          application/json:\n            schema: { $ref: '#/components/schemas/NewBook' }\n      responses:\n        '201':\n          description: Book created.\n          content:\n            application/json:\n              schema: { $ref: '#/components/schemas/Book' }\n  /books/{bookId}:\n    get:\n      summary: Get a book\n      parameters:\n        - name: bookId\n          in: path\n          required: true\n          schema: { type: string }\n      responses:\n        '200':\n          description: A book.\n          content:\n            application/json:\n              schema: { $ref: '#/components/schemas/Book' }\n        '404': { description: Book not found. }\n    delete:\n      parameters:\n        - name: bookId\n          in: path\n          required: true\n          schema: { type: string }\n      responses:\n        '204': { description: Book deleted. }\ncomponents:\n  schemas:\n    Book:\n      type: object\n      required: [id, title, author]\n      properties:\n        id: { type: string, example: bk_123 }\n        title: { type: string, example: The Odyssey }\n        author: { type: string, example: Homer }\n        publishedYear: { type: integer, example: -700 }\n    NewBook:\n      type: object\n      required: [title, author]\n      properties:\n        title: { type: string }\n        author: { type: string }\n");
    }
}
