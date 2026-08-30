---
id: SORT003
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])sort[-_]?fields?([-_]|$)", check: array }
---

# SORT003 — Multi-field sort parameter is not an array

## Intent

When a contract allows multiple sort fields, the schema should declare an
array so the serialization is explicit.

## Review-candidate example

```yaml
name: sort_fields
in: query
schema: { type: string }
```

## Compliant example

```yaml
name: sort_fields
in: query
schema:
  type: array
  items: { type: string }
```

## Detection and scope

The rule has `query-parameter` scope and reports a `sort_fields` parameter
matching the configured pattern when its schema type is not `array`.

## Configuration and limitations

`check: array` selects the representation check. The rule does not prescribe
array serialization; SORT004 covers the form style constraint.
