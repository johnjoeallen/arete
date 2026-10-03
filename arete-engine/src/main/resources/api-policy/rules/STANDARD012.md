---
id: STANDARD012
category: Standards
matcher: parameter
scope: parameter
parameters: { check: path-required }
---

# STANDARD012 — Path parameter is not marked required

## Intent

OpenAPI requires every path parameter to be `required: true`. A path parameter
without the flag is an invalid contract that generators and validators handle
inconsistently.

## Diagnostic

```yaml
/customers/{customerId}:
  get:
    parameters:
      - name: customerId
        in: path
        schema: { type: string }
```

## Compliant

```yaml
/customers/{customerId}:
  get:
    parameters:
      - name: customerId
        in: path
        required: true
        schema: { type: string }
```

## Detection and scope

The rule has `parameter` scope and uses the `parameter` rule with
`check: path-required`. Every parameter whose `in` is `path` is checked; one
diagnostic is reported per parameter whose `required` value is not `true`.

## Configuration and limitations

The rule reads the declared `required` value from the stable model. It
does not attempt to repair the contract or infer intent from the path
template.
