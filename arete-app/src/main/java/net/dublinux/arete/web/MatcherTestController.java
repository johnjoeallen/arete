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
import java.util.List;
import java.util.Map;

@Controller
public class MatcherTestController {
    private static final String MISSING_SUMMARY_SPEC = """
            openapi: 3.0.0
            info:
              title: Library API
              version: 1.0.0
            paths:
              /books:
                get:
                  responses:
                    '200': { description: OK }
            """;
    private static final String METHOD_SPEC = """
            openapi: 3.0.0
            info:
              title: Library API
              version: 1.0.0
            paths:
              /books:
                get:
                  summary: Delete a book
                  responses:
                    '200': { description: OK }
            """;
    private static final String REQUEST_BODY_SPEC = """
            openapi: 3.0.0
            info:
              title: Library API
              version: 1.0.0
            paths:
              /books:
                post:
                  summary: Create a book
                  responses:
                    '201': { description: Created }
            """;
    private static final String TITLE_SUFFIX_SPEC = """
            openapi: 3.0.0
            info:
              title: Library API
              version: 1.0.0
            paths:
              /health:
                get:
                  summary: Check service health
                  responses:
                    '200': { description: OK }
            """;
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
    public String test(@RequestParam String matcherId,
            @RequestParam String scope, @RequestParam String parameters,
            @RequestParam String matcherSource, @RequestParam String spec,
            @RequestParam(defaultValue = "missing-summary") String example, Model model) {
        populate(model);
        model.addAttribute("language", "distill");
        model.addAttribute("matcherId", matcherId);
        model.addAttribute("scope", scope);
        model.addAttribute("parameters", parameters);
        model.addAttribute("matcherSource", matcherSource);
        model.addAttribute("spec", spec);
        model.addAttribute("selectedExample", example);

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
            ValidationResult result = provider.testMatcher(new MatcherTestRequest("distill", matcherId, matcherSource,
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
                .map(e -> new SpecSummary(e.getRef(), e.getTitle(), e.getUpdatedAt().toEpochMilli()))
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
        model.addAttribute("matcherExamples", List.of(
                new MatcherTestExample("missing-summary", "Missing operation summaries",
                        "my-matcher", "operation", "{}", "none", """
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
                        """, MISSING_SUMMARY_SPEC),
                new MatcherTestExample("method-filter", "GET operations with mutation names",
                        "method-check", "operation", "{\"method\":\"GET\"}", "method", """
                        distill(api, rule) {
                            return api.paths
                                .expand { path ->
                                    path.operationDetails
                                        .filter { operation -> operation.method == rule.parameters["method"]
                                            && regexSearch("(?i).*(create|update|delete|remove).*",
                                                path.path + " " + operation.summary) }
                                        .map { operation ->
                                            occurrence(
                                                operation.pointer,
                                                operation.method + " " + path.path,
                                                "GET operation appears to mutate state")
                                        }
                                };
                        }
                        """, METHOD_SPEC),
                new MatcherTestExample("request-body", "POST operations without request bodies",
                        "request-body-check", "operation", "{\"method\":\"POST\"}", "method", """
                        distill(api, rule) {
                            return api.paths
                                .expand { path ->
                                    path.operationDetails
                                        .filter { operation ->
                                            operation.method == rule.parameters["method"]
                                                && !operation.requestBodyPresent
                                        }
                                        .map { operation ->
                                            occurrence(
                                                operation.pointer,
                                                operation.method + " " + path.path,
                                                "POST operation has no request body")
                                        }
                                };
                        }
                        """, REQUEST_BODY_SPEC),
                new MatcherTestExample("title-suffix", "API title suffix",
                        "title-check", "api", "{\"suffix\":\"Catalog\"}", "suffix", """
                        distill(api, rule) {
                            return !api.info.title.endsWith(rule.parameters["suffix"])
                                ? [occurrence(
                                    "/info/title",
                                    api.info.title,
                                    "API title does not end with the configured suffix")]
                                : [];
                        }
                        """, TITLE_SUFFIX_SPEC)));
        model.addAttribute("selectedExample", "missing-summary");
        model.addAttribute("spec", MISSING_SUMMARY_SPEC);
    }

    private record MatcherTestExample(String id, String label, String matcherId, String scope,
            String parameters, String parameterType, String source, String spec) {
    }
}
