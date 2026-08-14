package com.speculate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a request/response body example for a given media type, in a
 * format appropriate to that media type (JSON, XML, ...). Falls back to a
 * value synthesized from the schema when the spec has no explicit example.
 */
@Component
public class ExampleRenderer {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    public ExampleRenderer() {
        // swagger-parser resolves an explicit `format: date-time` example
        // value (e.g. "2024-01-01T00:00:00Z") to a real OffsetDateTime
        // rather than leaving it as a String, so these mappers need
        // JavaTimeModule to serialize it back out — without it, any example
        // containing a date-time field fails to render at all. Disabling
        // WRITE_DATES_AS_TIMESTAMPS keeps the output as the same ISO-8601
        // text the spec originally declared, instead of an epoch array.
        this.jsonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.registerModule(new JavaTimeModule());
        this.xmlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String render(MediaType mediaType, String contentType) {
        if (mediaType == null) {
            return "(no example available)";
        }

        Object example = normalize(extractExample(mediaType));
        if (example == null) {
            return "(no example available)";
        }

        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        try {
            if (ct.contains("x-www-form-urlencoded")) {
                return renderFormUrlEncoded(example);
            }
            if (ct.contains("xml")) {
                return xmlMapper.writer().withRootName("example").writeValueAsString(example);
            }
            return jsonMapper.writeValueAsString(example);
        } catch (Exception e) {
            return "(failed to render example: " + e.getMessage() + ")";
        }
    }

    /**
     * swagger-parser sometimes hands back examples as a Jackson {@link JsonNode}
     * tree rather than plain Map/List/primitive values (e.g. for inline YAML
     * {@code example:} blocks). Normalize to plain Java objects so the
     * instanceof-based format handling below works regardless of source.
     */
    private Object normalize(Object value) {
        if (value instanceof JsonNode node) {
            return normalize(jsonMapper.convertValue(node, Object.class));
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(entry.getKey(), normalize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(normalize(item));
            }
            return result;
        }
        return value;
    }

    private String renderFormUrlEncoded(Object example) {
        if (!(example instanceof Map<?, ?> map)) {
            return String.valueOf(example);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(String.valueOf(entry.getKey()), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(stringifyFormValue(entry.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String stringifyFormValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return jsonMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private Object extractExample(MediaType mediaType) {
        if (mediaType.getExample() != null) {
            return mediaType.getExample();
        }

        Map<String, Example> examples = mediaType.getExamples();
        if (examples != null && !examples.isEmpty()) {
            for (Example example : examples.values()) {
                if (example != null && example.getValue() != null) {
                    return example.getValue();
                }
            }
        }

        if (mediaType.getSchema() != null) {
            return SchemaExampleGenerator.generate(mediaType.getSchema(), 0);
        }

        return null;
    }

}
