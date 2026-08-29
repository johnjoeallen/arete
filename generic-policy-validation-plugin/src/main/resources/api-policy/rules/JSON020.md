---
id: JSON020
category: JSON
detector: example-validity
scope: property
parameters: { check: satisfies-constraints }
---

# JSON020 — Property example violates its own constraints

A property `example` should be a value the schema would accept. An example
that fails the property's `pattern`, length, range, or `enum` is misleading
and breaks example-driven tooling.

## Violation

```yaml
age:
  type: integer
  minimum: 0
  maximum: 120
  example: 999
countryCode:
  type: string
  pattern: '^[A-Z]{2}$'
  example: usa
```

## Compliant

```yaml
age:
  type: integer
  minimum: 0
  maximum: 120
  example: 34
countryCode:
  type: string
  pattern: '^[A-Z]{2}$'
  example: US
```

## Detection and scope

The rule has `property` scope and uses the `example-validity` detector with
`check: satisfies-constraints`. For each component-schema property that
declares an `example`, the detector checks `pattern` (RE2, unanchored),
`minLength` / `maxLength`, `minimum` / `maximum` (honouring
`exclusiveMinimum` / `exclusiveMaximum`), and `enum` membership.

## Configuration and limitations

Only scalar examples on component-schema properties are checked. Object and
array examples, and examples on inline request/response schemas, are not
validated here.
