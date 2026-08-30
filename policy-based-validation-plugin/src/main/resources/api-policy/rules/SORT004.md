---
id: SORT004
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: form }
---

# SORT004 — Sort parameter does not use form serialization

## Intent

Array-valued sort parameters should use standard form serialization unless a
policy explicitly chooses another style.

## Review-candidate example

```yaml
name: sort
in: query
style: simple
schema:
  type: array
  items: { type: string }
```

## Compliant example

```yaml
name: sort
in: query
style: form
schema:
  type: array
  items: { type: string }
```

## Detection and scope

The rule has `query-parameter` scope and reports matching sort or order
parameters whose explicit style is not `form`. Parameters with no explicit
style are accepted because OpenAPI defaults query arrays to form style.

## Configuration and limitations

`check: form` selects the style check. The rule does not inspect `explode` or
validate server-side serialization.
