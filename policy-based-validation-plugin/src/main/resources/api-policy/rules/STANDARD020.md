---
id: STANDARD020
category: Standards
matcher: schema-composition
scope: operation
parameters: { check: inline-body }
---

# STANDARD020 — Request or response body uses an inline object schema

## Intent

Request and response bodies should reference a reusable component schema. An
inline object schema cannot be reused, versioned, or referenced from a
generated model.

## Diagnostic

```yaml
post:
  requestBody:
    content:
      application/json:
        schema:
          type: object
          properties:
            name: { type: string }
```

## Compliant

```yaml
post:
  requestBody:
    content:
      application/json:
        schema: { $ref: '#/components/schemas/CreateCustomer' }
```

## Detection and scope

The rule has `operation` scope and uses the `schema-composition` rule with
`check: inline-body`. An operation whose request body, or any documented
response, declares an inline object schema (properties present, no `$ref`) is
reported.

## Configuration and limitations

Only object schemas are reported; inline scalars and arrays are allowed. The
rule inspects the media types exposed by the stable model.
