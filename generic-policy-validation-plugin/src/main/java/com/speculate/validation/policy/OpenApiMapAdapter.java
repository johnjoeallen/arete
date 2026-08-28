package com.speculate.validation.policy;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

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
                List<Map<String, Object>> segments = new ArrayList<>();
                for (String segment : entry.getKey().split("/")) {
                    if (!segment.isBlank() && !segment.startsWith("{")) {
                        segments.add(Map.of("name", segment, "pointer", path.get("pointer")));
                    }
                }
                path.put("segments", segments);
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
                        List<Map<String, Object>> parameters = new ArrayList<>();
                        addParameters(parameters, entry.getValue().getParameters(), path.get("pointer") + "/parameters");
                        if (operation != null) addParameters(parameters, operation.getParameters(), detail.get("pointer") + "/parameters");
                        detail.put("parameters", parameters);
                        operationDetails.add(detail);
                    }
                }
                path.put("operationDetails", operationDetails);
                paths.add(path);
            }
        }
        List<Map<String, Object>> schemas = new ArrayList<>();
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
                Schema schema = entry.getValue();
                Map<String, Object> schemaMap = new LinkedHashMap<>();
                schemaMap.put("name", entry.getKey());
                schemaMap.put("pointer", "/components/schemas/" + entry.getKey().replace("~", "~0").replace("/", "~1"));
                schemaMap.put("type", schema == null ? null : schema.getType());
                List<Map<String, Object>> properties = new ArrayList<>();
                if (schema != null && schema.getProperties() != null) {
                    for (Object propertyEntryObject : schema.getProperties().entrySet()) {
                        Map.Entry<?, ?> propertyEntry = (Map.Entry<?, ?>) propertyEntryObject;
                        if (!(propertyEntry.getKey() instanceof String propertyName) || !(propertyEntry.getValue() instanceof Schema property)) continue;
                        Map<String, Object> propertyMap = new LinkedHashMap<>();
                        propertyMap.put("name", propertyName);
                        propertyMap.put("pointer", schemaMap.get("pointer") + "/properties/" + propertyName.replace("~", "~0").replace("/", "~1"));
                        propertyMap.put("type", property.getType());
                        propertyMap.put("format", property.getFormat());
                        properties.add(propertyMap);
                    }
                }
                schemaMap.put("properties", properties);
                schemas.add(schemaMap);
            }
        }
        return Map.of("paths", paths, "schemas", schemas);
    }

    private static void addParameters(List<Map<String, Object>> destination, List<Parameter> source, String pointer) {
        if (source == null) return;
        for (int index = 0; index < source.size(); index++) {
            Parameter parameter = source.get(index);
            if (parameter == null || parameter.getName() == null || parameter.getIn() == null) continue;
            destination.add(Map.of("name", parameter.getName(), "in", parameter.getIn(), "pointer", pointer + "/" + index));
        }
    }
}
