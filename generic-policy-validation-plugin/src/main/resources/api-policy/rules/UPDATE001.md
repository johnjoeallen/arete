---
id: UPDATE001
category: Update semantics
detector: operation-semantics
scope: operation
parameters:
  method: PUT
  match: partial-update
---

# UPDATE001 — PUT appears to perform a partial update

## Intent

PUT normally communicates replacement of the complete representation at a
known resource. If it updates only selected fields, clients may accidentally
erase omitted data or misunderstand the operation’s idempotency and merge
semantics. This rule highlights possible partial PUT updates for review; it
cannot prove the server’s behavior from an OpenAPI contract.

## Detection and scope

The rule has `operation` scope and uses the `operation-semantics` detector:

```yaml
parameters:
  method: PUT
  match: partial-update
```

For each PUT operation, the detector concatenates the path template and
operation summary, then performs a case-insensitive word-boundary search for
`partial`, `patch`, or `update`. A matching operation is reported at its
operation pointer with the message `PUT appears to perform a partial update`.
The path and summary are the only inspected fields; request and response
schemas, descriptions, methods, and payloads do not affect matching.

## Review-candidate example

This operation is reported because its summary contains “partial update”:

```yaml
openapi: 3.0.3
info: { title: Customer API, version: 1.0.0 }
paths:
  /customers/{customer_id}:
    put:
      summary: Partially update customer contact details
      responses: { '204': { description: Updated } }
```

Reviewers should decide whether PUT replaces the complete customer or whether
the operation should be PATCH (with explicitly documented patch semantics).

## Compliant example

This PUT summary contains no partial-update signal and does not match:

```yaml
paths:
  /customers/{customer_id}:
    put:
      summary: Replace customer
      requestBody:
        required: true
        content:
          application/json: { schema: { $ref: '#/components/schemas/Customer' } }
      responses: { '200': { description: Replaced } }
```

This absence of a finding is not proof that the implementation replaces the
whole resource.

## Parameters, references, and limitations

The bundled parameters are fixed to `method: PUT` and `match: partial-update`.
The detector supports other matching modes, but they are not part of
UPDATE001. It does not inspect `$ref` targets, request bodies, response codes,
or runtime traffic. A partial-update description that uses different wording
may be missed, while an update-related word used harmlessly may be flagged.
Findings are heuristic candidates for human review, not automatic compliance
decisions.
