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
                        List<String> mediaTypes = new ArrayList<>();
                        if (operation != null && operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                            mediaTypes.addAll(operation.getRequestBody().getContent().keySet());
                        }
                        List<Map<String, Object>> responses = new ArrayList<>();
                        if (operation != null && operation.getResponses() != null) {
                            for (Map.Entry<String, ?> responseEntry : operation.getResponses().entrySet()) {
                                Object response = responseEntry.getValue();
                                Map<String, Object> responseMap = new LinkedHashMap<>();
                                responseMap.put("status", responseEntry.getKey());
                                responseMap.put("description", responseProperty(response, "getDescription"));
                                Object headers = responseProperty(response, "getHeaders");
                                responseMap.put("headers", headers instanceof Map<?, ?> map ? new ArrayList<>(map.keySet()) : List.of());
                                Object content = responseProperty(response, "getContent");
                                if (content instanceof Map<?, ?> map) mediaTypes.addAll(map.keySet().stream().map(Object::toString).toList());
                                responses.add(responseMap);
                            }
                        }
                        detail.put("responses", responses);
                        detail.put("mediaTypes", mediaTypes);
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
                schemaMap.put("array", schema != null && "array".equals(schema.getType()));
                schemaMap.put("maxItems", schema == null ? null : schema.getMaxItems());
                List<Map<String, Object>> properties = new ArrayList<>();
                if (schema != null && schema.getProperties() != null) {
                    for (Object propertyEntryObject : schema.getProperties().entrySet()) {
                        Map.Entry<?, ?> propertyEntry = (Map.Entry<?, ?>) propertyEntryObject;
                        if (!(propertyEntry.getKey() instanceof String propertyName) || !(propertyEntry.getValue() instanceof Schema property)) continue;
                        Map<String, Object> propertyMap = new LinkedHashMap<>();
                        propertyMap.put("name", propertyName);
                        propertyMap.put("pointer", schemaMap.get("pointer") + "/properties/" + propertyName.replace("~", "~0").replace("/", "~1"));
                        propertyMap.put("type", property.getType());
                        propertyMap.put("array", property != null && "array".equals(property.getType()));
                        propertyMap.put("maxItems", property.getMaxItems());
                        propertyMap.put("format", property.getFormat());
                        propertyMap.put("nullable", Boolean.TRUE.equals(property.getNullable()));
                        propertyMap.put("required", schema.getRequired() != null && schema.getRequired().contains(propertyName));
                        propertyMap.put("enumPresent", property.getEnum() != null && !property.getEnum().isEmpty());
                        properties.add(propertyMap);
                    }
                }
                schemaMap.put("properties", properties);
                schemas.add(schemaMap);
            }
        }
        Map<String, Object> info = new LinkedHashMap<>();
        if (openApi.getInfo() != null) {
            info.put("title", openApi.getInfo().getTitle());
            info.put("description", openApi.getInfo().getDescription());
            info.put("version", openApi.getInfo().getVersion());
            if (openApi.getInfo().getContact() != null) {
                info.put("contactName", openApi.getInfo().getContact().getName());
                info.put("contactEmail", openApi.getInfo().getContact().getEmail());
            }
        }
        if (openApi.getExtensions() != null) info.put("apiId", openApi.getExtensions().get("x-api-id"));
        return Map.of("paths", paths, "schemas", schemas, "info", info);
    }

    private static void addParameters(List<Map<String, Object>> destination, List<Parameter> source, String pointer) {
        if (source == null) return;
        for (int index = 0; index < source.size(); index++) {
            Parameter parameter = source.get(index);
            if (parameter == null || parameter.getName() == null || parameter.getIn() == null) continue;
            destination.add(Map.of("name", parameter.getName(), "in", parameter.getIn(), "pointer", pointer + "/" + index));
        }
    }

    /** Reads parser response properties without exposing the parser response type to Groovy. */
    private static Object responseProperty(Object response, String getter) {
        if (response == null) return null;
        try { return response.getClass().getMethod(getter).invoke(response); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
}
