package com.apiv.openapiviewer.service;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthesizes a representative example value from a schema when the spec
 * itself doesn't supply an explicit example.
 */
final class SchemaExampleGenerator {

    private static final int MAX_DEPTH = 6;

    private SchemaExampleGenerator() {
    }

    @SuppressWarnings("unchecked")
    static Object generate(Schema<?> schema, int depth) {
        if (schema == null || depth > MAX_DEPTH) {
            return null;
        }
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().get(0);
        }

        String type = schema.getType();

        if ("object".equals(type) || (type == null && schema.getProperties() != null)) {
            Map<String, Object> obj = new LinkedHashMap<>();
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                for (Map.Entry<String, Schema> entry : properties.entrySet()) {
                    obj.put(entry.getKey(), generate(entry.getValue(), depth + 1));
                }
            }
            return obj;
        }

        if ("array".equals(type)) {
            List<Object> list = new ArrayList<>();
            if (schema.getItems() != null) {
                list.add(generate(schema.getItems(), depth + 1));
            }
            return list;
        }

        if ("string".equals(type)) {
            return sampleString(schema.getFormat());
        }
        if ("integer".equals(type)) {
            return 0;
        }
        if ("number".equals(type)) {
            return 0.0;
        }
        if ("boolean".equals(type)) {
            return Boolean.TRUE;
        }

        return null;
    }

    private static String sampleString(String format) {
        if (format == null) {
            return "string";
        }
        return switch (format) {
            case "date" -> "2024-01-01";
            case "date-time" -> "2024-01-01T00:00:00Z";
            case "email" -> "user@example.com";
            case "uuid" -> "3fa85f64-5717-4562-b3fc-2c963f66afa6";
            case "uri", "url" -> "https://example.com";
            case "byte" -> "base64string";
            case "password" -> "********";
            default -> "string";
        };
    }

}
