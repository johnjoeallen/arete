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
    private static final java.util.regex.Pattern PATH_TEMPLATE = java.util.regex.Pattern.compile("\\{([^}]+)}");

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
                List<String> templateParameters = new ArrayList<>();
                java.util.regex.Matcher templateMatcher = PATH_TEMPLATE.matcher(entry.getKey());
                while (templateMatcher.find()) templateParameters.add(templateMatcher.group(1));
                path.put("templateParameters", templateParameters);
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
                        detail.put("operationId", operation == null ? null : operation.getOperationId());
                        detail.put("tags", operation == null || operation.getTags() == null ? List.of() : List.copyOf(operation.getTags()));
                        detail.put("requestBodyPresent", operation != null && operation.getRequestBody() != null);
                        detail.put("requestBodyRequired", operation != null && operation.getRequestBody() != null
                                && Boolean.TRUE.equals(operation.getRequestBody().getRequired()));
                        detail.put("security", operation == null || operation.getSecurity() == null ? null : securityRequirements(operation.getSecurity()));
                        List<String> mediaTypes = new ArrayList<>();
                        List<String> requestMediaTypes = new ArrayList<>();
                        if (operation != null && operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                            requestMediaTypes.addAll(operation.getRequestBody().getContent().keySet());
                            mediaTypes.addAll(requestMediaTypes);
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
                                List<Map<String, Object>> headerDetails = new ArrayList<>();
                                if (headers instanceof Map<?, ?> map) {
                                    for (Map.Entry<?, ?> headerEntry : map.entrySet()) {
                                        Object schema = responseProperty(headerEntry.getValue(), "getSchema");
                                        Object content = responseProperty(headerEntry.getValue(), "getContent");
                                        headerDetails.add(Map.of(
                                                "name", String.valueOf(headerEntry.getKey()),
                                                "schemaPresent", schema != null || content != null));
                                    }
                                }
                                responseMap.put("headerDetails", headerDetails);
                                Object content = responseProperty(response, "getContent");
                                List<String> schemaTypes = new ArrayList<>();
                                List<String> responseMediaTypes = new ArrayList<>();
                                if (content instanceof Map<?, ?> map) {
                                    responseMediaTypes.addAll(map.keySet().stream().map(Object::toString).toList());
                                    mediaTypes.addAll(responseMediaTypes);
                                    map.values().forEach(media -> { Object schema = responseProperty(media, "getSchema"); Object type = responseProperty(schema, "getType"); if (type != null) schemaTypes.add(type.toString()); });
                                }
                                responseMap.put("schemaTypes", schemaTypes);
                                responseMap.put("mediaTypes", responseMediaTypes);
                                responses.add(responseMap);
                            }
                        }
                        detail.put("responses", responses);
                        detail.put("mediaTypes", mediaTypes);
                        detail.put("requestMediaTypes", requestMediaTypes);
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
                        propertyMap.put("enumValues", property.getEnum() == null ? List.of() : property.getEnum());
                        propertyMap.put("extensibleEnum", property.getExtensions() != null && property.getExtensions().containsKey("x-extensible-enum"));
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
        info.put("openapiVersion", openApi.getOpenapi());
        if (openApi.getExtensions() != null) {
            info.put("apiId", openApi.getExtensions().get("x-api-id"));
            info.put("audience", openApi.getExtensions().get("x-audience"));
        }
        List<String> servers = openApi.getServers() == null ? List.of() : openApi.getServers().stream().map(server -> server.getUrl()).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paths", paths);
        result.put("schemas", schemas);
        result.put("info", info);
        result.put("servers", servers);
        result.put("security", openApi.getSecurity() == null ? null : securityRequirements(openApi.getSecurity()));
        return result;
    }

    private static void addParameters(List<Map<String, Object>> destination, List<Parameter> source, String pointer) {
        if (source == null) return;
        for (int index = 0; index < source.size(); index++) {
            Parameter parameter = source.get(index);
            if (parameter == null || parameter.getName() == null || parameter.getIn() == null) continue;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", parameter.getName());
            detail.put("in", parameter.getIn());
            detail.put("pointer", pointer + "/" + index);
            detail.put("required", Boolean.TRUE.equals(parameter.getRequired()));
            detail.put("schemaPresent", parameter.getSchema() != null || parameter.getContent() != null);
            detail.put("style", parameter.getStyle());
            detail.put("explode", parameter.getExplode());
            detail.put("schemaType", parameter.getSchema() == null ? null : parameter.getSchema().getType());
            detail.put("schemaMaximum", parameter.getSchema() == null ? null : parameter.getSchema().getMaximum());
            destination.add(detail);
        }
    }

    private static List<Map<String, Object>> securityRequirements(List<io.swagger.v3.oas.models.security.SecurityRequirement> requirements) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (io.swagger.v3.oas.models.security.SecurityRequirement requirement : requirements) {
            Map<String, Object> entry = new LinkedHashMap<>();
            requirement.forEach(entry::put);
            result.add(entry);
        }
        return result;
    }

    /** Reads parser response properties without exposing parser response types to detectors. */
    private static Object responseProperty(Object response, String getter) {
        if (response == null) return null;
        try { return response.getClass().getMethod(getter).invoke(response); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
}
