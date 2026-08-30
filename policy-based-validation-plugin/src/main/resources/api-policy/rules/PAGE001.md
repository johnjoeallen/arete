---
id: PAGE001
category: Pagination
matcher: pagination
scope: operation
parameters: { name-pattern: "(^|[-_])(page|offset|cursor)([-_]|$)", check: present }
---

# PAGE001 — Collection lacks a pagination control

## Intent

Collection `GET` operations should document a page, offset, or cursor query
parameter when their result set can grow.

## Review-candidate example

```yaml
parameters: []
```

## Compliant example

```yaml
parameters:
  - { name: page, in: query, schema: { type: integer } }
```

## Detection and scope

The rule has `operation` scope and checks collection `GET` operations whose
path has no template parameter. It reports when no query parameter name
matches `page`, `offset`, or `cursor`.

## Configuration and limitations

`name-pattern` selects the pagination-control names and `check: present`
selects the presence check. The rule does not require a particular pagination
algorithm or response shape.
