package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The parser coerces enum values to the property's type; the model gives rules the values as written. */
class EnumValueTypesTest {
    @SuppressWarnings("unchecked")
    private static List<Object> enumValues(String spec, String property) {
        var parsed = new OpenAPIV3Parser().readContents(spec, null, new ParseOptions());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), spec);
        for (Object p : (List<Object>) api.get("schemaProperties")) {
            Map<String, Object> map = (Map<String, Object>) p;
            if (property.equals(map.get("name"))) return (List<Object>) map.get("enumValues");
        }
        throw new AssertionError("no property " + property);
    }

    private static final String YAML = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths: {}
            components:
              schemas:
                C:
                  type: object
                  properties:
                    mixed: { type: string, enum: [1, '2', true, 2.5] }
                    tier: { type: string, enum: [GOLD, SILVER] }
                    nested:
                      type: object
                      properties:
                        code: { type: string, enum: [7] }
            """;

    @Test
    void valuesKeepTheTypesTheyWereWrittenWith() {
        assertEquals(List.of(1L, "2", true, 2.5), enumValues(YAML, "mixed"));
        assertEquals(List.of("GOLD", "SILVER"), enumValues(YAML, "tier"));
    }

    @Test
    void propertiesBelowInlineObjectsAreRestoredToo() {
        assertEquals(List.of(7L), enumValues(YAML, "code"));
    }

    @Test
    void aJsonDocumentIsReadTheSameWay() {
        String json = """
                {"openapi":"3.0.0","info":{"title":"T","version":"1.0.0"},"paths":{},
                 "components":{"schemas":{"C":{"type":"object","properties":{
                   "mixed":{"type":"string","enum":[1,"2"]}}}}}}
                """;
        assertEquals(List.of(1L, "2"), enumValues(json, "mixed"));
    }
}
