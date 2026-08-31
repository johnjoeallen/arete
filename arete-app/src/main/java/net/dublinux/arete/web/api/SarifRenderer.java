package net.dublinux.arete.web.api;

import net.dublinux.arete.web.api.AutomationApiController.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders findings as a minimal SARIF 2.1.0 log for GitHub code scanning and
 * other SARIF consumers. One run, one tool ("Areté"), one result per finding.
 */
final class SarifRenderer {

    private SarifRenderer() {
    }

    static Map<String, Object> render(List<Finding> findings) {
        List<Map<String, Object>> rules = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Integer> ruleIndex = new LinkedHashMap<>();

        for (Finding f : findings) {
            String ruleId = f.ruleId() == null ? "unknown" : f.ruleId();
            int index = ruleIndex.computeIfAbsent(ruleId, id -> {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("id", id);
                if (f.title() != null) {
                    rule.put("name", f.title());
                    rule.put("shortDescription", Map.of("text", f.title()));
                }
                if (f.documentationUrl() != null) {
                    rule.put("helpUri", f.documentationUrl());
                }
                rules.add(rule);
                return rules.size() - 1;
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleId", ruleId);
            result.put("ruleIndex", index);
            result.put("level", sarifLevel(f.severity()));
            result.put("message", Map.of("text", f.message() == null ? ruleId : f.message()));
            if (f.pointer() != null) {
                result.put("locations", List.of(Map.of(
                        "logicalLocations", List.of(Map.of("fullyQualifiedName", f.pointer())))));
            }
            results.add(result);
        }

        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "Areté");
        driver.put("informationUri", "https://johnjoeallen.github.io/arete/");
        driver.put("rules", rules);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", Map.of("driver", driver));
        run.put("results", results);

        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("version", "2.1.0");
        sarif.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
        sarif.put("runs", List.of(run));
        return sarif;
    }

    private static String sarifLevel(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "ERROR" -> "error";
            case "WARNING" -> "warning";
            default -> "note";
        };
    }
}
