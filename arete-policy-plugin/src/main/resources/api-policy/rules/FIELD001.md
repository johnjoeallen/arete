---
id: FIELD001
category: Collection capabilities
matcher: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])(fields|select)([-_]|$)", check: present }
---

# FIELD001 — Collection lacks a field-selection capability

## Intent

Collection operations may expose a field-selection query parameter so clients
can request only the representation fields they need.

## Review-candidate example

```yaml
parameters: []
```

## Compliant example

```yaml
parameters:
  - { name: fields, in: query, schema: { type: string } }
```

## Detection and scope

The rule has `operation` scope and checks collection `GET` operations whose
path has no template parameter. It reports when no query parameter name
matches the configured field-selection pattern.

## Configuration and limitations

`name-pattern` selects names containing `fields` or `select`; `check: present`
selects the presence check. The rule does not prescribe selected fields or
their syntax and does not inspect item schemas.
