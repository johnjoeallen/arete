package net.dublinux.arete.engine.report;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A finding is named by what it is about, not by where it sits in a list, so that adding a parameter, a tag or a
 * response does not make every later finding look new.
 */
class StablePointersTest {
    private static final String BASE = """
            openapi: 3.0.0
            info: { title: Orders, version: 1.0.0 }
            tags:
              - name: orders
              - name: shipments
            paths:
              /orders:
                get:
                  tags: [orders]
                  parameters:
                    - { name: limit, in: query, schema: { type: integer } }
                    - { name: cursor, in: query, schema: { type: string } }
                  responses:
                    '200': { description: OK }
                    '404': { description: Not found }
                    '429': { description: Slow down }
            """;

    /** Same API with a new, documented parameter first, a new tag first, and a new response between the others. */
    private static final String HEAD = BASE
            .replace("  - name: orders\n", "  - name: audit\n  - name: orders\n")
            .replace("                    - { name: limit", "                    - { name: offset, in: query, description: Where to start, example: 0, schema: { type: integer } }\n                    - { name: limit")
            .replace("                    '404'", "                    '400': { description: Bad request }\n                    '404'");

    private static Engine engine() { return Engine.builder().cacheDir(null).build(); }

    private static ScoreReport report(String text) {
        return ScoreReport.of(engine(), "orders.yaml", text, "Enterprise Grade", Overrides.none());
    }

    private static Set<String> pointers(ScoreReport report, String rule) {
        Set<String> pointers = new TreeSet<>();
        for (Finding f : report.findings()) if (f.ruleId().equals(rule)) pointers.add(f.pointer());
        return pointers;
    }

    @Test
    void parametersAreNamedByInAndName() {
        assertEquals(Set.of("/paths/~1orders/get/parameters/query:cursor", "/paths/~1orders/get/parameters/query:limit"),
                pointers(report(BASE), "DOC014"));
    }

    @Test
    void addingAParameterATagOrAResponseDoesNotMoveTheFindingsOfTheOthers() {
        ScoreReport base = report(BASE);
        ScoreReport head = report(HEAD);

        assertEquals(pointers(base, "DOC014"), pointers(head, "DOC014"), "limit and cursor keep their pointers");

        ScoreDiff diff = ScoreDiff.of(base, head);
        for (ScoreDiff.Change change : diff.changes()) {
            // Everything the head reports about the unchanged parts of the API is the same finding as before.
            if (change.finding().pointer() != null && change.finding().pointer().contains("query:limit")) {
                assertEquals(ScoreDiff.Kind.EXISTING, change.kind(), change.finding().toString());
            }
        }
        assertEquals(0, diff.count(ScoreDiff.Kind.RESOLVED), "nothing the base reported has gone: " + diff.of(ScoreDiff.Kind.RESOLVED));
    }

    @Test
    void eachResponseOfAnOperationHasItsOwnPointer() {
        String text = BASE.replace("'200': { description: OK }", "'200': { description: OK, headers: { Link: { description: next } } }")
                .replace("'429': { description: Slow down }", "'429': { description: Slow down, headers: { Link: { description: next } } }");

        ScoreReport report = report(text);

        // STANDARD017 reports a response header with no schema; each is under its own response.
        assertEquals(Set.of("/paths/~1orders/get/responses/200/headers/Link", "/paths/~1orders/get/responses/429/headers/Link"),
                pointers(report, "STANDARD017"));
    }

    @Test
    void aTagFindingNamesTheTag() {
        String text = BASE.replace("name: shipments", "name: Shipment Tracking").replace("tags: [orders]", "tags: [Shipment Tracking]");

        ScoreReport report = ScoreReport.of(engine(), "orders.yaml", text, "Zalando Extended", Overrides.none());
        assertEquals(Set.of("/tags/Shipment Tracking"), pointers(report, "CASE008"));
    }

    @Test
    void theLocatorFindsListElementsByTheirNames() {
        PointerLocator at = PointerLocator.of(BASE);

        assertEquals(11, at.locate("/paths/~1orders/get/parameters/query:limit").line());
        assertEquals(12, at.locate("/paths/~1orders/get/parameters/query:cursor").line());
        assertEquals(5, at.locate("/tags/shipments").line());
        assertEquals(15, at.locate("/paths/~1orders/get/responses/404").line());
        // The old index form still works.
        assertEquals(12, at.locate("/paths/~1orders/get/parameters/1").line());
        // A name that is not there falls back to the list.
        assertEquals(10, at.locate("/paths/~1orders/get/parameters/query:nope").line());
    }

    @Test
    void theLocatorFollowsAParameterWrittenAsARef() {
        String text = """
                openapi: 3.0.0
                info: { title: T, version: 1.0.0 }
                paths:
                  /orders:
                    get:
                      parameters:
                        - { name: other, in: query, schema: { type: string } }
                        - $ref: '#/components/parameters/Limit'
                      responses: { '200': { description: OK } }
                components:
                  parameters:
                    Limit: { name: limit, in: query, schema: { type: integer } }
                """;

        assertEquals(8, PointerLocator.of(text).locate("/paths/~1orders/get/parameters/query:limit").line());
    }

    @Test
    void aServerIsFoundByItsUrl() {
        String text = "openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\nservers:\n  - url: https://a.example.test\n  - url: https://b.example.test\npaths: {}\n";

        assertEquals(5, PointerLocator.of(text).locate("/servers/https:~1~1b.example.test").line());
    }

    @Test
    void findingsThatSharedAPointerAreToldApartByTheirSubject() {
        // Two bad servers are both reported at /servers; the URL (the finding's path) keeps them apart.
        ScoreReport base = ScoreReport.of(engine(), "s.yaml",
                "openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\nservers:\n  - url: http://one.test/v1/\npaths: {}\n", "Zalando Extended", Overrides.none());
        ScoreReport head = ScoreReport.of(engine(), "s.yaml",
                "openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\nservers:\n  - url: http://one.test/v1/\n  - url: http://two.test/v1/\npaths: {}\n", "Zalando Extended", Overrides.none());

        ScoreDiff diff = ScoreDiff.of(base, head);

        assertTrue(diff.count(ScoreDiff.Kind.NEW) > 0);
        assertTrue(diff.of(ScoreDiff.Kind.NEW).stream().allMatch(c -> c.finding().path() != null && c.finding().path().contains("two.test")),
                diff.of(ScoreDiff.Kind.NEW).toString());
        assertEquals(0, diff.count(ScoreDiff.Kind.RESOLVED));
    }
}
