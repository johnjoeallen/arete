---
id: JSON018
category: JSON
matcher: schema
scope: property
parameters: { type: integer, format: absent }
---

# JSON018 — Integer property does not declare a format

## Intent

An `integer` property should declare `format: int32` or `format: int64` so
that clients pick the right numeric type and range.

## Diagnostic

```yaml
quantity:
  type: integer
```

## Compliant

```yaml
quantity:
  type: integer
  format: int32
```

## Detection and scope

The rule has `property` scope and uses the `schema` rule with
`type: integer` and `format: absent`. A component-schema property whose type
is `integer` and that declares no `format` is reported.

## Configuration and limitations

The rule checks for the presence of a `format`, not that the value is one
of `int32` / `int64`. Inline request and response schemas are not inspected.
