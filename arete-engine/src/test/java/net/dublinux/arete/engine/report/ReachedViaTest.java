package net.dublinux.arete.engine.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A defect on a shared definition is one finding at the definition, with the shortest route to it from an operation. */
class ReachedViaTest {
    private static final String SPEC = """
            openapi: 3.0.0
            info: { title: Shared, version: 1.0.0 }
            paths:
              /orders:
                post:
                  requestBody: { $ref: '#/components/requestBodies/NewOrder' }
                  responses:
                    '201': { description: Created }
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema: { $ref: '#/components/schemas/Order' }
              /customers:
                get:
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema: { type: array, items: { $ref: '#/components/schemas/Address' } }
            components:
              requestBodies:
                NewOrder:
                  content:
                    application/json:
                      schema: { $ref: '#/components/schemas/Order' }
              schemas:
                Order:
                  type: object
                  properties:
                    billing: { $ref: '#/components/schemas/Address' }
                    parent: { $ref: '#/components/schemas/Order' }
                Address:
                  type: object
                  properties:
                    display name: { type: string }
                Unused:
                  type: object
                  properties:
                    display name: { type: string }
            """;

    @Test
    void theRouteIsTheShortestFromAnOperationThroughEachDefinition() {
        RefPaths routes = RefPaths.of(SPEC);
        assertEquals(List.of("GET /customers", "Address"), routes.routeTo("/components/schemas/Address/properties/display name"));
        assertEquals(List.of("GET /orders", "Order"), routes.routeTo("/components/schemas/Order"));
        assertEquals(List.of("GET /orders", "Order"), routes.routeTo("/components/schemas/Order/properties/parent"),
                "a recursive schema does not lengthen or loop the route");
    }

    @Test
    void aRouteThroughAComponentThatIsNotASchemaNamesIt() {
        String spec = """
                openapi: 3.0.0
                info: { title: Bodies, version: 1.0.0 }
                paths:
                  /orders:
                    post:
                      requestBody: { $ref: '#/components/requestBodies/NewOrder' }
                      responses:
                        '201': { description: Created }
                components:
                  requestBodies:
                    NewOrder:
                      content:
                        application/json:
                          schema: { $ref: '#/components/schemas/Order' }
                  schemas:
                    Order: { type: object }
                """;
        assertEquals(List.of("POST /orders", "requestBodies/NewOrder", "Order"), RefPaths.of(spec).routeTo("/components/schemas/Order"));
    }

    @Test
    void anUnreachedDefinitionAndAnythingOutsideComponentsHaveNoRoute() {
        RefPaths routes = RefPaths.of(SPEC);
        assertEquals(List.of(), routes.routeTo("/components/schemas/Unused/properties/display name"));
        assertEquals(List.of(), routes.routeTo("/paths/~1orders/get"));
        assertEquals(List.of(), routes.routeTo(null));
        assertEquals(List.of(), RefPaths.of("a: [unclosed").routeTo("/components/schemas/A"));
    }

    @Test
    void oneDefectReachedByManyOperationsIsOneFindingWithItsRoute() throws Exception {
        ScoreReport report = ScoreReport.of(Engine.builder().cacheDir(null).build(), "api.yaml", SPEC, null, Overrides.none());
        List<Finding> findings = report.findings().stream().filter(f -> f.ruleId().equals("JSON003")).toList();
        assertEquals(2, findings.size(), "one at Address and one at the unused schema, not one per route: " + findings);

        Finding address = findings.stream().filter(f -> f.pointer().contains("/Address/")).findFirst().orElseThrow();
        assertEquals(List.of("GET /customers", "Address"), address.reachedVia());
        assertEquals("GET /customers → Address", address.reachedViaText());
        Finding unused = findings.stream().filter(f -> f.pointer().contains("/Unused/")).findFirst().orElseThrow();
        assertEquals(List.of(), unused.reachedVia());
        assertNull(unused.reachedViaText());

        assertTrue(ReportWriter.markdown(List.of(report), true).contains("(reached via GET /customers → Address)"));
        assertTrue(ReportWriter.markdown(List.of(report), false).contains("(reached via GET /customers → Address)"));
        JsonNode json = new ObjectMapper().readTree(ReportWriter.json(List.of(report)));
        JsonNode shown = null;
        for (JsonNode f : json.get("specs").get(0).get("findings")) if (f.get("pointer").asText().contains("/Address/")) shown = f;
        assertEquals("GET /customers", shown.get("reachedVia").get(0).asText());
        assertTrue(ReportWriter.sarif(List.of(report)).contains("\"reachedVia\" : \"GET /customers → Address\"")
                || ReportWriter.sarif(List.of(report)).contains("\"reachedVia\":\"GET /customers → Address\""));
    }

    @Test
    void theRouteIsNotPartOfAFindingsIdentity() {
        Finding bare = new Finding("R", "t", "WARNING", "Warning", "/components/schemas/A", null, "m", 1, 1, 0.5);
        Finding routed = new Finding("R", "t", "WARNING", "Warning", "/components/schemas/A", null, "m", 1, 1, 0.5, List.of("GET /a", "A"));
        assertEquals(bare.identity(), routed.identity());
    }
}
