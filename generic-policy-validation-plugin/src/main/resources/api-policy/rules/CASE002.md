---
id: CASE002
category: Naming
matcher: naming
scope: path-parameter
parameters: { convention: snake_case, match: non-conforming }
---

# CASE002 — Path parameter is not snake_case

## Intent

Path parameter names should follow a predictable snake_case convention so the
template and its parameter declaration remain easy to connect.

## Detection and scope

The rule has `path-parameter` scope and uses the `naming` rule:

```yaml
parameters: { convention: snake_case, match: non-conforming }
```

It examines declared operation parameters whose `in` value is `path`. Names
must match `[a-z][a-z0-9]*(?:_[a-z0-9]+)*`; non-conforming names are reported at
the parameter pointer with `Name does not use the configured convention`.

## Review-candidate example

```yaml
paths:
  /customers/{customerId}:
    get:
      parameters:
        - in: path
          name: customerId
          required: true
          schema: { type: string }
      responses: { '200': { description: OK } }
```

Rename the parameter to `customer_id` and update the path template together.

## Compliant example

```yaml
paths:
  /customers/{customer_id}:
    get:
      parameters:
        - in: path
          name: customer_id
          required: true
          schema: { type: string }
      responses: { '200': { description: OK } }
```

## Parameters, references, and limitations

The rule checks only declared path parameters and the fixed snake_case grammar.
It does not verify that a parameter matches a template, inspect schemas or
runtime routing, or check path segments unrelated to parameters. Referenced
parameters count only after host normalisation.
