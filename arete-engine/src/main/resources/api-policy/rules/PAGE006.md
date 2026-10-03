---
id: PAGE006
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])cursor([-_]|$)", check: string }
---

# PAGE006 — Cursor parameter is not a string

## Intent

Cursor-based pagination controls should use strings so opaque cursors can be
changed without imposing a numeric representation on clients.

## Review-candidate example

```yaml
name: cursor
in: query
schema: { type: integer }
```

## Compliant example

```yaml
name: cursor
in: query
schema: { type: string }
```

## Detection and scope

The rule has `query-parameter` scope and reports a `cursor` parameter whose
schema type is not `string`.

## Configuration and limitations

`check: string` selects the representation check. The rule does not validate
cursor opacity, expiry, ordering, or server-side pagination behaviour.
