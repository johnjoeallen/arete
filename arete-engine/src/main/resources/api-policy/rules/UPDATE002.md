---
id: UPDATE002
category: Update semantics
matcher: operation
scope: operation
parameters: { method: PATCH }
---

# UPDATE002 — PATCH is used

## Intent

PATCH represents partial modification of a resource. It can avoid sending a
complete representation when a client needs to change only a few fields, but
it also requires clients and servers to agree on patch semantics and conflict
handling. Its use is a policy choice rather than an inherently invalid
design. A finding is therefore a policy review candidate, not proof that the
operation is wrong.

## Detection and scope

The rule has `operation` scope and uses the `operation` rule:

```yaml
id: UPDATE002
category: Update semantics
matcher: operation
scope: operation
parameters: { method: PATCH }
```

The rule checks each OpenAPI operation’s HTTP method. Every `PATCH`
operation produces an diagnostic, pointing to that operation and displaying a
message equivalent to `PATCH operation is used`. No summary, path name,
request-body schema, response, or payload content is inspected. The rule does
not determine whether the PATCH is a standards-compliant patch document or
whether the operation actually performs a partial update at runtime.

## Review-candidate example

This operation is reported because its method is PATCH:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /customers/{customer_id}:
    patch:
      summary: Update selected customer fields
      parameters:
        - in: path
          name: customer_id
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/merge-patch+json:
            schema:
              type: object
              properties:
                email: { type: string, format: email }
      responses:
        '200':
          description: Updated customer
```

A representative request body could be:

```json
{ "email": "new@example.test" }
```

Reviewers should confirm that the operation’s partial-update semantics are
documented, that omitted fields have an unambiguous meaning, and that the
chosen patch media type matches the server behavior.

## Compliant example

This operation does not produce an UPDATE002 diagnostic because its method is
PUT, not PATCH:

```yaml
paths:
  /customers/{customer_id}:
    put:
      summary: Replace customer
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/Customer' }
      responses:
        '200':
          description: Replaced customer
```

The absence of an UPDATE002 finding does not establish that PUT is correctly
used or that the API has no partial-update behavior; other rules and human
review may still be appropriate.

## Parameters, references, and limitations

The bundled rule supplies only `method: PATCH`. The operation rule accepts
additional parameters such as `summary` and `request-body`, but those are not
part of UPDATE002 and changing the rule metadata would change its contract.
There is no `match` mode, severity setting, or configuration that makes this
rule distinguish full replacement from partial modification.

The rule operates on normalised operation facts and reports operations
from the host’s parsed `paths` collection. Referenced request bodies,
responses, and schemas do not affect matching. Missing or unresolved
references can affect the surrounding OpenAPI model, but they cannot turn a
non-PATCH method into a match. Path-level parameters and descriptions are
likewise irrelevant.

The rule does not inspect `POST`, `PUT`, or `DELETE` semantics, runtime HTTP
traffic, JSON Patch operations, merge-patch fields, idempotency, conditional
requests, authorization, or response status codes. It intentionally answers
only the narrow question “is this operation declared with PATCH?” and leaves
the design and implementation assessment to policy reviewers.
