---
id: STANDARD026
category: Standards
matcher: parameter
scope: operation
parameters: { check: unique }
---

# STANDARD026 — Parameter is declared more than once

## Intent

OpenAPI identifies a parameter by the combination of its `name` and its
location (`in`). Declaring the same `name` + `in` twice on one operation —
often once at the path-item level and again on the operation, or by a
copy-paste slip — is an invalid contract. Tools variously take the first, take
the last, or reject the document, so the effective behaviour is undefined.

## Detection and scope

The rule has `operation` scope and uses the `parameter` matcher with
`check: unique`. Path-item and operation parameters are considered together.
Parameters are grouped by `in` + `name`; for any group with more than one
member the first is left alone and each later declaration is reported at its
own pointer.

## Diagnostic

```yaml
/orders/{orderId}:
  parameters:
    - { name: orderId, in: path, required: true, schema: { type: string } }
  get:
    parameters:
      - { name: orderId, in: path, required: true, schema: { type: integer } }
    responses: { '200': { description: OK } }
```

The operation-level `orderId` is reported — it duplicates the path-item one.

## Compliant

```yaml
/orders/{orderId}:
  parameters:
    - { name: orderId, in: path, required: true, schema: { type: string } }
  get:
    responses: { '200': { description: OK } }
```

## Configuration and limitations

`check: unique` is the rule's only mode. It compares `name` and `in` exactly
and case-sensitively; it does not merge or diff the two declarations' schemas,
nor flag a path-item parameter that an operation deliberately overrides with an
identical declaration (that is still a duplicate by the specification's rule).
