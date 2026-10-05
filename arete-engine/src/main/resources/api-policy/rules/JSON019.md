---
id: JSON019
category: JSON
matcher: schema
scope: property
parameters: { type: number, format: absent }
---

# JSON019 — Number property does not declare a format

## Intent

A `number` property should declare `format: float` or `format: double` so
that clients pick the right numeric type and precision.

## Diagnostic

```yaml
price:
  type: number
```

## Compliant

```yaml
price:
  type: number
  format: double
```

## Detection and scope

The rule has `property` scope and uses the `schema` rule with
`type: number` and `format: absent`. A component-schema property whose type is
`number` and that declares no `format` is reported.

## Configuration and limitations

The rule checks for the presence of a `format`, not that the value is one
of `float` / `double`. Inline request and response schemas are not inspected.
