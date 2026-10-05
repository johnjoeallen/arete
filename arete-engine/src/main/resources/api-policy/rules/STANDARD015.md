---
id: STANDARD015
category: Standards
matcher: request-body
scope: operation
parameters: { check: required-flag-missing }
---

# STANDARD015 — Request body is not marked required

## Intent

`POST`, `PUT`, and `PATCH` operations that declare a request body should set
`required: true`. Without it, OpenAPI treats the body as optional and
generated clients and server stubs may accept an empty payload.

## Diagnostic

```yaml
post:
  requestBody:
    content:
      application/json:
        schema: { $ref: '#/components/schemas/Customer' }
```

## Compliant

```yaml
post:
  requestBody:
    required: true
    content:
      application/json:
        schema: { $ref: '#/components/schemas/Customer' }
```

## Detection and scope

The rule has `operation` scope and uses the `request-body` rule with
`check: required-flag-missing`. An operation is reported when it declares a
request body whose `required` value is not `true`.

## Configuration and limitations

The rule does not restrict the check to specific methods; a policy that
only cares about mutating methods should pair this rule with method-semantics
rules. It reads the declared flag and does not infer intent from the schema.
