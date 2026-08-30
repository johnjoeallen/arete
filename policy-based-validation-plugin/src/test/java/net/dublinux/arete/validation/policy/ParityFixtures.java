package net.dublinux.arete.validation.policy;

/** Shared OpenAPI fixtures for rule cross-runtime parity tests. */
final class ParityFixtures {
    private ParityFixtures() {}

    static final String CATALOGUE_SPEC = """
            openapi: 3.0.0
            info: { title: Catalogue API, version: 1.0.0, openapi: 3.0.0 }
            servers: [ { url: https://api.example.com/v1 } ]
            paths:
              /orders:
                post:
                  responses:
                    '200':
                      description: OK
                      headers:
                        X-Trace: { description: trace id }
                        X-Count: { description: total, schema: { type: integer } }
                delete:
                  requestBody: { content: { application/json: { schema: { type: object } } } }
                  responses: { '204': { description: gone } }
              /orders/{orderId}:
                get:
                  parameters:
                    - { name: orderId, in: path, required: true, schema: { type: string } }
                    - { name: api_key, in: query, schema: { type: string } }
                    - { name: X-SSN, in: header, schema: { type: string } }
                  responses:
                    '200':
                      description: OK
                      content:
                        text/xml: { schema: { type: string } }
                        '*/*': { schema: { type: string } }
              /reports:
                get:
                  parameters:
                    - { name: search, in: query, schema: { type: string } }
                    - { name: fields, in: query, schema: { type: object } }
                  responses:
                    '200': { description: OK }
              /bulk-create/orders/{batchId}:
                post:
                  summary: Bulk create orders
                  responses: { '200': { description: OK } }
              /catalog/searchByName:
                put:
                  summary: Search catalog by criteria
                  responses: { '200': { description: OK } }
              /things/{thingKey}:
                get:
                  parameters:
                    - name: thingId
                      in: path
                      schema: { type: string }
                    - { name: verbose, in: query }
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema: { type: object, properties: { a: { type: string } } }
                    '500': { description: boom }
            components:
              schemas:
                CreateOrderRequest:
                  type: object
                  properties:
                    id: { type: integer }
                    created: { type: string }
                    expiresAt: { type: string, format: date-time }
                    startTime: { type: string, format: date-time }
                    password: { type: string }
                order_response:
                  type: object
                  properties:
                    modified: { type: string, format: date-time }
                Composed:
                  allOf:
                    - $ref: '#/components/schemas/order_response'
                    - type: object
                      properties: { extra: { type: string } }
            """;

    static final String LINT_SPEC = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths:
              /x:
                get:
                  responses:
                    200: { description: ok }
                    '404':
                      description: nope
                      content: { application/json: { schema: { $ref: '#/components/schemas/Ghost' } } }
            """;

    static final String HOUSE_STYLE_SPEC = """
            openapi: 3.0.0
            info: { title: draft payments poc, version: v2 }
            x-weird: true
            servers: [ { url: https://internal.corp/v2 } ]
            paths:
              /v2/Payments:
                get:
                  summary: get all the payments that exist
                  parameters:
                    - name: tags
                      in: query
                      style: spaceDelimited
                      schema: { type: array, items: { type: string } }
                  responses:
                    '200':
                      description: error occurred
                      content: { application/json: { schema: { type: string } } }
                    '429': { description: too many }
                    '500': { description: boom }
            """;

    static final String SCHEMA_SPEC = """
            openapi: 3.0.0
            info:
              title: Widget API
              description: Widgets.
              contact: { name: Team, email: team@example.com }
              version: 1.2
            components:
              securitySchemes:
                bearerAuth: { type: http, scheme: bearer }
              schemas:
                Widget:
                  type: object
                  required: [id, name]
                  properties:
                    id: { type: integer }
                    name: { type: string }
                    price: { type: number }
                    count: { type: integer, format: int32 }
                    tags: { type: array, items: { type: string } }
                    status: { type: string, enum: [ACTIVE, inactive, 3] }
                    kind:
                      type: string
                      enum: [A, B]
                      x-extensible-enum: [A, B]
                    code: { type: string, pattern: '^[A-Z]{3}$', minLength: 3, maxLength: 3, example: 'ab' }
                    ratio: { type: number, minimum: 1, maximum: 10, example: 42 }
                  example:
                    id: 1
                WidgetList:
                  type: object
                  properties:
                    items: { type: array, items: { $ref: '#/components/schemas/Widget' } }
            paths:
              /widgets:
                get:
                  security: []
                  responses: { '200': { description: ok } }
                post:
                  responses:
                    '201':
                      description: created
                      headers:
                        X-Widget-Token: { description: token }
            security:
              - bearerAuth: []
            """;

    static final String OPS_SPEC = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths:
              /orders:
                get:
                  operationId: listOrders
                  tags: [orders]
                  responses: { '200': { description: ok } }
                post:
                  operationId: listOrders
                  tags: [orders]
                  responses: { '200': { description: ok } }
              /orders/{id}/items/{itemId}/tags:
                get:
                  responses:
                    '200': { description: ok }
                    '400':
                      description: bad
                      content: { application/json: { example: { error: nope } } }
                    '422':
                      description: bad too
                      content: { application/json: { example: { error: nope } } }
              /customers:
                get: { responses: { '200': { description: ok } } }
              /invoices:
                get: { responses: { '200': { description: ok } } }
            """;

}
