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

/** A defect on a shared definition is one finding at the definition, with the routes in from each operation that reaches it. */
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
    void everyOperationThatReachesADefinitionGetsItsShortestRoute() {
        RefPaths routes = RefPaths.of(SPEC);
        assertEquals(List.of(
                List.of("GET /customers", "Address"),
                List.of("GET /orders", "Order", "Address"),
                List.of("POST /orders", "requestBodies/NewOrder", "Order", "Address")),
                routes.routesTo("/components/schemas/Address/properties/display name"));
        assertEquals(List.of(
                List.of("GET /orders", "Order"),
                List.of("POST /orders", "requestBodies/NewOrder", "Order")),
                routes.routesTo("/components/schemas/Order/properties/parent"),
                "a recursive schema does not add routes or loop");
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
        assertEquals(List.of(List.of("POST /orders", "requestBodies/NewOrder", "Order")), RefPaths.of(spec).routesTo("/components/schemas/Order"));
    }

    @Test
    void anUnreachedDefinitionAndAnythingOutsideComponentsHaveNoRoute() {
        RefPaths routes = RefPaths.of(SPEC);
        assertEquals(List.of(), routes.routesTo("/components/schemas/Unused/properties/display name"));
        assertEquals(List.of(), routes.routesTo("/paths/~1orders/get"));
        assertEquals(List.of(), routes.routesTo(null));
        assertEquals(List.of(), RefPaths.of("a: [unclosed").routesTo("/components/schemas/A"));
    }

    @Test
    void oneDefectReachedByManyOperationsIsOneFindingWithItsRoute() throws Exception {
        ScoreReport report = ScoreReport.of(Engine.builder().cacheDir(null).build(), "api.yaml", SPEC, null, Overrides.none());
        List<Finding> findings = report.findings().stream().filter(f -> f.ruleId().equals("JSON003")).toList();
        assertEquals(2, findings.size(), "one at Address and one at the unused schema, not one per route: " + findings);

        Finding address = findings.stream().filter(f -> f.pointer().contains("/Address/")).findFirst().orElseThrow();
        assertEquals(3, address.routes().size());
        assertEquals("GET /customers → Address", Finding.routeText(address.routes().get(0)));
        Finding unused = findings.stream().filter(f -> f.pointer().contains("/Unused/")).findFirst().orElseThrow();
        assertEquals(List.of(), unused.routes());

        String markdown = ReportWriter.markdown(List.of(report), true);
        assertTrue(markdown.contains("(reached via GET /customers → Address; GET /orders → Order → Address; "
                + "POST /orders → requestBodies/NewOrder → Order → Address)"), markdown);
        assertTrue(ReportWriter.markdown(List.of(report), false).contains("reached via GET /customers → Address"));
        JsonNode json = new ObjectMapper().readTree(ReportWriter.json(List.of(report)));
        JsonNode shown = null;
        for (JsonNode f : json.get("specs").get(0).get("findings")) if (f.get("pointer").asText().contains("/Address/")) shown = f;
        assertEquals(3, shown.get("reachedVia").size());
        assertEquals("GET /customers", shown.get("reachedVia").get(0).get(0).asText());
        JsonNode sarif = new ObjectMapper().readTree(ReportWriter.sarif(List.of(report)));
        boolean sarifRoutes = false;
        for (JsonNode result : sarif.get("runs").get(0).get("results"))
            if (result.has("properties")) sarifRoutes |= result.get("properties").get("reachedVia").get(0).asText().equals("GET /customers → Address");
        assertTrue(sarifRoutes);
    }

    @Test
    void theMarkdownNamesAFewRoutesAndCountsTheRest() {
        List<List<String>> five = List.of(List.of("A"), List.of("B"), List.of("C"), List.of("D"), List.of("E"));
        Finding f = new Finding("R", "t", "WARNING", "Warning", "/components/schemas/S", null, "m", 1, 1, 0.5, five);
        ScoreReport report = new ScoreReport("api.yaml", "P", "SUCCESS", null, 99, 99, null, null, 1, List.of(f), List.of());
        assertTrue(ReportWriter.markdown(List.of(report), true).contains("(reached via A; B; C; and 2 more)"));
        assertEquals(5, f.routes().size(), "the data keeps them all");
    }

    @Test
    void theRouteIsNotPartOfAFindingsIdentity() {
        Finding bare = new Finding("R", "t", "WARNING", "Warning", "/components/schemas/A", null, "m", 1, 1, 0.5);
        Finding routed = new Finding("R", "t", "WARNING", "Warning", "/components/schemas/A", null, "m", 1, 1, 0.5, List.of(List.of("GET /a", "A")));
        assertEquals(bare.identity(), routed.identity());
    }
}
