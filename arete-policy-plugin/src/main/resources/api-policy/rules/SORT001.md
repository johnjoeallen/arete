---
id: SORT001
category: Collection capabilities
matcher: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: present }
---

# SORT001 — Collection lacks a sort capability

## Intent

Collection `GET` operations should document how clients request a stable sort
order.

## Review-candidate example

```yaml
parameters: []
```

## Compliant example

```yaml
parameters:
  - { name: sort, in: query, schema: { type: string } }
```

## Detection and scope

The rule has `operation` scope and checks collection `GET` operations whose
path has no template parameter. It reports when no query parameter name
matches `sort` or `order`.

## Configuration and limitations

`name-pattern` selects sort and order names; `check: present` selects the
presence check. The rule does not prescribe sort syntax or sortable fields.
