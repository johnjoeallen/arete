package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the rule model reads {@code $ref}: followed through chains, nested, and safe against cycles. */
class OpenApiMapAdapterRefsTest {
    private static Map<String, Object> model(String spec) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(spec, null, options);
        assertNotNull(parsed.getOpenAPI(), String.valueOf(parsed.getMessages()));
        return OpenApiMapAdapter.toMap(parsed.getOpenAPI());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) { return (List<Map<String, Object>>) value; }

    private static Map<String, Object> byName(List<Map<String, Object>> items, String name) {
        return items.stream().filter(item -> name.equals(item.get("name"))).findFirst().orElseThrow();
    }

    private static final String CYCLES = """
            openapi: 3.0.0
            info: { title: Cycles, version: 1.0.0 }
            paths:
              /x:
                post:
                  requestBody: { $ref: '#/components/requestBodies/R1' }
                  responses:
                    '200': { $ref: '#/components/responses/A' }
                    '404': { $ref: '#/components/responses/Gone' }
            components:
              requestBodies:
                R1: { $ref: '#/components/requestBodies/R2' }
                R2: { $ref: '#/components/requestBodies/R1' }
              responses:
                A: { $ref: '#/components/responses/B' }
                B: { $ref: '#/components/responses/A' }
              schemas:
                Node:
                  type: object
                  properties:
                    next: { $ref: '#/components/schemas/Node' }
                    leaf: { $ref: '#/components/schemas/Leaf' }
                Leaf: { type: object, properties: { value: { type: string } } }
                Alias1: { $ref: '#/components/schemas/Alias2' }
                Alias2: { $ref: '#/components/schemas/Alias1' }
            """;

    @Test
    void cyclesAndDanglingRefsAreRecordedNotFollowedForever() {
        Map<String, Object> model = model(CYCLES);
        List<Map<String, Object>> problems = list(model.get("refProblems"));
        assertTrue(problems.stream().anyMatch(p -> "cycle".equals(p.get("reason")) && String.valueOf(p.get("ref")).contains("requestBodies")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> "cycle".equals(p.get("reason")) && String.valueOf(p.get("ref")).contains("responses")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> "missing".equals(p.get("reason")) && String.valueOf(p.get("ref")).endsWith("/Gone")), problems.toString());
    }

    @Test
    void aRecursiveSchemaIsNotACycleProblemAndStopsWhereItRecurs() {
        Map<String, Object> model = model(CYCLES);
        List<Map<String, Object>> schemas = list(model.get("schemas"));
        // Node -> leaf -> value is two property hops; Node -> next -> Node adds nothing new.
        assertEquals(2, byName(schemas, "Node").get("nestingDepth"));
        assertEquals(1, byName(schemas, "Leaf").get("nestingDepth"));
    }

    private static final String NESTED = """
            openapi: 3.0.0
            info: { title: Nested, version: 1.0.0 }
            paths: {}
            components:
              schemas:
                Order:
                  type: object
                  required: [address]
                  properties:
                    address:
                      type: object
                      required: [street]
                      properties:
                        street: { type: string }
                        geo:
                          type: object
                          properties:
                            lat: { type: number }
                    lines:
                      type: array
                      items:
                        type: object
                        properties:
                          sku: { type: string }
                    owner: { $ref: '#/components/schemas/Person' }
                Person:
                  type: object
                  properties:
                    name: { type: string, description: Full name, format: name-format }
                Composed:
                  allOf:
                    - $ref: '#/components/schemas/Person'
                    - type: object
                      properties:
                        extra: { type: string }
            """;

    @Test
    void propertiesBelowInlineObjectsItemsAndCompositionMembersAreVisibleAtTheirOwnPointers() {
        List<Map<String, Object>> schemas = list(model(NESTED).get("schemas"));
        List<Map<String, Object>> order = list(byName(schemas, "Order").get("properties"));
        List<Object> pointers = order.stream().map(p -> p.get("pointer")).toList();
        assertTrue(pointers.contains("/components/schemas/Order/properties/address/properties/street"), pointers.toString());
        assertTrue(pointers.contains("/components/schemas/Order/properties/address/properties/geo/properties/lat"), pointers.toString());
        assertTrue(pointers.contains("/components/schemas/Order/properties/lines/items/properties/sku"), pointers.toString());
        Map<String, Object> lat = order.stream().filter(p -> "lat".equals(p.get("name"))).findFirst().orElseThrow();
        assertEquals(3, lat.get("depth"));
        // required is judged against the schema that declares the property.
        Map<String, Object> street = order.stream().filter(p -> "street".equals(p.get("name"))).findFirst().orElseThrow();
        assertEquals(true, street.get("required"));
        assertEquals(false, lat.get("required"));
        assertEquals(3, byName(schemas, "Order").get("nestingDepth"));

        List<Map<String, Object>> composed = list(byName(schemas, "Composed").get("properties"));
        assertEquals("/components/schemas/Composed/allOf/1/properties/extra", composed.get(0).get("pointer"));
    }

    @Test
    void aReferencedSchemaIsReportedAtItsDefinitionNotRepeatedUnderEveryPropertyThatUsesIt() {
        List<Map<String, Object>> schemas = list(model(NESTED).get("schemas"));
        List<Map<String, Object>> order = list(byName(schemas, "Order").get("properties"));
        assertTrue(order.stream().noneMatch(p -> "name".equals(p.get("name"))), "Person.name must not be listed under Order");
        assertEquals(1, list(model(NESTED).get("schemaProperties")).stream().filter(p -> "name".equals(p.get("name"))).count());
    }

    @Test
    void aReferenceProperty_readsLikeTheSchemaItNames() {
        List<Map<String, Object>> order = list(byName(list(model(NESTED).get("schemas")), "Order").get("properties"));
        Map<String, Object> owner = order.stream().filter(p -> "owner".equals(p.get("name"))).findFirst().orElseThrow();
        assertEquals("object", owner.get("type"));
        assertEquals("#/components/schemas/Person", owner.get("ref"));
    }

    private static final String LONG_CHAIN;
    static {
        StringBuilder chain = new StringBuilder("""
                openapi: 3.0.0
                info: { title: Chain, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    Start: { $ref: '#/components/schemas/S0' }
                """);
        int links = OpenApiMapAdapter.MAX_REF_CHAIN + 8;
        for (int i = 0; i < links; i++) chain.append("    S").append(i).append(": { $ref: '#/components/schemas/S").append(i + 1).append("' }\n");
        chain.append("    S").append(links).append(": { type: string }\n");
        LONG_CHAIN = chain.toString();
    }

    @Test
    void aChainLongerThanTheLimitIsReportedNotSilentlyTruncated() {
        List<Map<String, Object>> problems = list(model(LONG_CHAIN).get("refProblems"));
        assertTrue(problems.stream().anyMatch(p -> "chain-too-long".equals(p.get("reason"))), problems.toString());
    }

    @Test
    void aDeepDiamondOfReferencesIsWalkedOncePerSchemaNotOncePerPath() {
        int levels = 40;
        StringBuilder spec = new StringBuilder("""
                openapi: 3.0.0
                info: { title: Diamond, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                """);
        for (int i = 0; i < levels; i++) {
            spec.append("    L").append(i).append(": { type: object, properties: { a: { $ref: '#/components/schemas/L").append(i + 1)
                    .append("' }, b: { $ref: '#/components/schemas/L").append(i + 1).append("' } } }\n");
        }
        spec.append("    L").append(levels).append(": { type: object, properties: { v: { type: string } } }\n");
        long start = System.nanoTime();
        List<Map<String, Object>> schemas = list(model(spec.toString()).get("schemas"));
        assertEquals(levels + 1, byName(schemas, "L0").get("nestingDepth"));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000, "2^40 paths would never finish");
    }
}
