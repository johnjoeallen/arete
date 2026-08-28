package com.speculate.validation.policy;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts parser-owned models into the small, stable map contract for detectors. */
final class OpenApiMapAdapter {
    private OpenApiMapAdapter() { }

    static Map<String, Object> toMap(OpenAPI openApi) {
        List<Map<String, Object>> paths = new ArrayList<>();
        if (openApi.getPaths() != null) {
            for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
                Map<String, Object> path = new LinkedHashMap<>();
                path.put("path", entry.getKey());
                path.put("pointer", "/paths/" + entry.getKey().replace("~", "~0").replace("/", "~1"));
                List<String> operations = entry.getValue() == null ? List.of() : entry.getValue().readOperationsMap().keySet().stream()
                        .map(method -> method.name()).toList();
                path.put("operations", operations);
                List<Map<String, Object>> operationDetails = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : entry.getValue().readOperationsMap().entrySet()) {
                        Operation operation = operationEntry.getValue();
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("method", operationEntry.getKey().name());
                        detail.put("pointer", path.get("pointer") + "/" + operationEntry.getKey().name().toLowerCase());
                        detail.put("summary", operation == null ? null : operation.getSummary());
                        detail.put("requestBodyPresent", operation != null && operation.getRequestBody() != null);
                        operationDetails.add(detail);
                    }
                }
                path.put("operationDetails", operationDetails);
                paths.add(path);
            }
        }
        return Map.of("paths", paths);
    }
}
