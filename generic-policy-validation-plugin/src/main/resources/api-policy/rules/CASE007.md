---
id: CASE007
category: Naming
detector: schema-name
scope: schema
parameters: { pattern: "(?i).*(request|response)", case: pascal-case }
---

# CASE007 — Request/response object is not PascalCase

Where a programme keeps the `Request` / `Response` suffix on payload schemas,
those names should be PascalCase (`CreateCustomerRequest`, `CustomerResponse`)
so generated model classes are idiomatic.

## Violation

```yaml
components:
  schemas:
    create_customer_request: { type: object }
    customerResponse: { type: object }
```

## Compliant

```yaml
components:
  schemas:
    CreateCustomerRequest: { type: object }
    CustomerResponse: { type: object }
```

## Detection and scope

The rule has `schema` scope and uses the `schema-name` detector with
`case: pascal-case`. A component schema whose name ends in `request` or
`response` (case-insensitively) and does not match `[A-Z][A-Za-z0-9]*` is
reported.

## Configuration and limitations

This rule is **mutually exclusive with REST005 / REST006**, which flag the
`Request` / `Response` suffix itself. A policy chooses one convention:
Enterprise Grade keeps REST005/REST006 and does not enable CASE007. The
PascalCase check is a heuristic — an all-caps name passes.
