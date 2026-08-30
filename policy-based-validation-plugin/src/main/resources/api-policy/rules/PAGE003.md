---
id: PAGE003
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])limit([-_]|$)", check: integer }
---

# PAGE003 — Page-size parameter is not an integer

## Intent

A page-size limit should be expressed as an integer query parameter.

## Review-candidate example

```yaml
name: limit
in: query
schema: { type: string }
```

## Compliant example

```yaml
name: limit
in: query
schema: { type: integer, maximum: 100 }
```

## Detection and scope

The rule has `query-parameter` scope and reports a `limit` parameter whose
schema type is not `integer`.

## Configuration and limitations

`check: integer` selects the representation check. The rule does not enforce a
maximum; PAGE004 covers that separate constraint.
