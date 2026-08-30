---
id: STANDARD024
category: Standards
matcher: component-usage
scope: api
parameters: { check: unreferenced-schema }
---

# STANDARD024 — Component schema is never referenced

## Intent

A schema under `components/schemas` that no `$ref` points at is dead weight:
it inflates the document, misleads readers about the surface area, and often
marks a reference that was renamed or deleted without cleaning up its target.

## Detection and scope

The rule has `api` scope and uses the `component-usage` matcher with
`check: unreferenced-schema`. The host collects every `$ref` string in the
raw document; the rule reports each `components/schemas` entry whose
`#/components/schemas/<name>` target appears in none of them.

## Review-candidate example

```yaml
paths:
  /orders:
    get:
      responses:
        '200':
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }
components:
  schemas:
    Order: { type: object }
    LegacyOrder: { type: object }
```

`LegacyOrder` is reported.

## Limitations

The match is textual and exact: a schema referenced only by an unusual `$ref`
form, or one used solely as the top-level request/response schema by name
elsewhere, may be reported. The rule looks at `components/schemas` only, not
responses, parameters, or other component types.
