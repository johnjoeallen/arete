---
id: JSON017
category: JSON
detector: enum-values
scope: property
parameters: { check: no-duplicates }
---

# JSON017 — Enum contains duplicate values

An `enum` list should not repeat a value. A duplicate is almost always a
copy-paste error and produces ambiguous generated code.

## Violation

```yaml
status:
  type: string
  enum: [ACTIVE, INACTIVE, ACTIVE]
```

## Compliant

```yaml
status:
  type: string
  enum: [ACTIVE, INACTIVE, PENDING]
```

## Detection and scope

The rule has `property` scope and uses the `enum-values` detector with
`check: no-duplicates`. Each schema property that declares an `enum` is
checked; a property whose list contains a repeated value is reported once.

## Configuration and limitations

Values are compared by their string form. The detector inspects component
schema properties; it does not resolve inline request or response schemas.
