---
id: STANDARD011
category: Standards
matcher: parameter
scope: operation
parameters: { check: max-count, maximum: 8 }
---

# STANDARD011 — Operation declares too many parameters

An operation with a large number of parameters is hard to call correctly and
usually signals that filtering, projection, or a request body would model the
input better. The active policy sets an upper bound; the default is
{{maximum}}.

## Diagnostic

```yaml
/reports:
  get:
    parameters:
      - { name: a, in: query, schema: { type: string } }
      - { name: b, in: query, schema: { type: string } }
      # ...more than the configured maximum...
```

## Compliant

```yaml
/reports:
  get:
    parameters:
      - { name: filter, in: query, schema: { type: string } }
      - { name: fields, in: query, schema: { type: string } }
```

## Detection and scope

The rule has `operation` scope and uses the `parameter` rule with
`check: max-count`. Path-level and operation-level parameters are counted
together per operation. An operation is reported once when the count exceeds
`maximum`.

## Configuration and limitations

`maximum` is a policy parameter. The rule counts declared parameters only;
it does not weigh a parameter's importance, inspect `$ref` fan-out, or account
for parameters supplied through a request body.
