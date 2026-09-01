---
id: SORT002
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: string }
---

# SORT002 — Sort parameter is not a string expression

## Intent

Sort fields and directions should be represented as a string expression that
can be extended without changing the parameter type.

## Review-candidate example

```yaml
name: sort
in: query
schema: { type: array }
```

## Compliant example

```yaml
name: sort
in: query
schema: { type: string }
```

## Detection and scope

The rule has `query-parameter` scope and reports `sort` or `order` parameters
whose schema type is not `string`.

## Configuration and limitations

`check: string` selects the representation check. The rule does not define
sort direction syntax or validate that requested fields are sortable.
