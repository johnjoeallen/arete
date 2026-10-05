---
id: STANDARD011
category: Standards
matcher: parameter
scope: operation
parameters: { check: max-count, maximum: 8 }
---

# STANDARD011 — Operation declares too many parameters

## Intent

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

`maximum` is a policy parameter. Each reported operation also carries its
parameter count as a measured value, so a `format: 2` policy can tier on it:

```yaml
STANDARD011:
  measure: value
  tiers: { 5: 0.5, 9: 1, 13: 3 }   # 5-8 parameters costs 0.5, 9-12 costs 1, 13 or more costs 3
```

The matcher only reports operations above `maximum`, which is 8 unless the policy says otherwise. When a rule is charged
by value and the policy does not set `maximum`, the engine sets it to one below the lowest tier, so the example above
runs with `maximum: 4` and the 5-8 tier works. Set it yourself to override that:

```yaml
  parameters: { maximum: 0 }       # report every operation, with its count, even those below the first tier
```

`maximum: 0` costs nothing extra (operations under the first tier are not charged) but, once any operation reaches a tier,
every operation with parameters is listed as a finding. Use it when you want that full picture, not for the limit itself.
The cost follows the operation
with the most parameters. The rule counts declared parameters only;
it does not weigh a parameter's importance, inspect `$ref` fan-out, or account
for parameters supplied through a request body.
