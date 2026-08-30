---
id: PAGE002
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(page|offset)([-_]|$)", check: integer }
---

# PAGE002 — Page or offset parameter is not an integer

## Intent

Offset-based pagination controls should declare an integer schema.

## Review-candidate example

```yaml
name: page
in: query
schema: { type: string }
```

## Compliant example

```yaml
name: page
in: query
schema: { type: integer }
```

## Detection and scope

The rule has `query-parameter` scope and reports `page` or `offset` parameters
whose schema type is not `integer`.

## Configuration and limitations

`check: integer` selects the representation check. The rule does not validate
minimum values, bounds, or server-side pagination.
