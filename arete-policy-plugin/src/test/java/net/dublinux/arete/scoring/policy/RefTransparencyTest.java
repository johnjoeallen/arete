package net.dublinux.arete.scoring.policy;

import net.dublinux.arete.scoring.spi.Diagnostic;
import net.dublinux.arete.scoring.spi.ScoringResult;
import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecInput;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code $ref} is a way of writing a definition once, not a different spec: a document that
 * defines something inline and the same document that defines it through {@code components}
 * must score identically. Each pair below states the same API both ways.
 */
class RefTransparencyTest {
    private static final String[] POLICIES = {"Enterprise Grade", "Zalando", "Zalando Extended"};

    private static final String INLINE = """
            openapi: 3.0.0
            info: { title: Ref API, version: 1.0.0, description: An API. }
            paths:
              /orders:
                get:
                  summary: List orders
                  description: Lists orders.
                  parameters:
                    - { name: limit, in: query, description: Page size, schema: { type: integer, maximum: 100 } }
                    - { name: cursor, in: query, description: Page cursor, schema: { type: string } }
                  responses:
                    '200':
                      description: OK
                      headers:
                        X-Rate-Limit: { description: Calls left, schema: { type: integer } }
                      content:
                        application/json:
                          schema: { type: array, items: { type: object, properties: { id: { type: string, description: Order id } } } }
                    '404':
                      description: Not found
                      content:
                        application/problem+json:
                          schema: { type: object, properties: { title: { type: string, description: Title, example: Nope } } }
                post:
                  summary: Create order
                  description: Creates an order.
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema: { type: object, properties: { id: { type: string, description: Order id, example: o-1 } } }
                  responses:
                    '201':
                      description: Created
                      headers:
                        Location: { description: Where, schema: { type: string } }
            """;

    private static final String REFS = """
            openapi: 3.0.0
            info: { title: Ref API, version: 1.0.0, description: An API. }
            paths:
              /orders:
                get:
                  summary: List orders
                  description: Lists orders.
                  parameters:
                    - $ref: '#/components/parameters/LimitAlias'
                    - $ref: '#/components/parameters/Cursor'
                  responses:
                    '200': { $ref: '#/components/responses/OrderListAlias' }
                    '404': { $ref: '#/components/responses/NotFound' }
                post:
                  summary: Create order
                  description: Creates an order.
                  requestBody: { $ref: '#/components/requestBodies/OrderAlias' }
                  responses:
                    '201': { $ref: '#/components/responses/Created' }
            components:
              parameters:
                LimitAlias: { $ref: '#/components/parameters/Limit' }
                Limit: { name: limit, in: query, description: Page size, schema: { type: integer, maximum: 100 } }
                Cursor: { name: cursor, in: query, description: Page cursor, schema: { type: string } }
              headers:
                RateLimit: { description: Calls left, schema: { type: integer } }
                RateLimitAlias: { $ref: '#/components/headers/RateLimit' }
                Where: { description: Where, schema: { type: string } }
              requestBodies:
                OrderAlias: { $ref: '#/components/requestBodies/Order' }
                Order:
                  required: true
                  content:
                    application/json: { schema: { $ref: '#/components/schemas/Order' } }
              responses:
                OrderListAlias: { $ref: '#/components/responses/OrderList' }
                OrderList:
                  description: OK
                  headers:
                    X-Rate-Limit: { $ref: '#/components/headers/RateLimitAlias' }
                  content:
                    application/json: { schema: { $ref: '#/components/schemas/OrderPage' } }
                NotFound:
                  description: Not found
                  content:
                    application/problem+json: { schema: { $ref: '#/components/schemas/Problem' } }
                Created:
                  description: Created
                  headers:
                    Location: { $ref: '#/components/headers/Where' }
              schemas:
                OrderPage: { type: array, items: { $ref: '#/components/schemas/Order' } }
                Order: { type: object, properties: { id: { $ref: '#/components/schemas/OrderId' } } }
                OrderId: { type: string, description: Order id, example: o-1 }
                Problem: { type: object, properties: { title: { type: string, description: Title, example: Nope } } }
            """;

    @Test
    void operationsScoreTheSameWhetherDefinedInlineOrThroughRefs() {
        for (String policy : POLICIES) {
            Set<String> inline = findings(INLINE, policy);
            // STANDARD020 reports inline body schemas, which is a difference the refs form is meant to have.
            inline.remove("STANDARD020");
            assertEquals(inline, findings(REFS, policy), "policy " + policy);
        }
    }

    /**
     * The rules that fire, ignoring counts and pointers: an inline schema and a component schema live at
     * different places, and a definition used twice reports once, where inlining it twice reports twice.
     */
    private static Set<String> findings(String spec, String policy) {
        PolicyScoringPlugin plugin = new PolicyScoringPlugin();
        plugin.configure(Map.of());
        ScoringResult result = plugin.score(SpecInput.builder().content(spec).format(SpecFormat.OPENAPI3).policy(policy).build());
        assertEquals(ScoringResult.Status.SUCCESS, result.getStatus(), String.valueOf(result.getErrorMessage()));
        Set<String> rules = new TreeSet<>();
        for (Diagnostic d : result.getDiagnostics()) rules.add(d.getRuleId());
        return rules;
    }
}
