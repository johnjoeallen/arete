---
id: FILTER001
category: Collection capabilities
matcher: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])filter([-_]|$)", check: present }
---

# FILTER001 — Collection lacks a filter capability

## Intent

Collection `GET` operations should expose a documented filter query parameter
when clients need to select a subset of resources.

## Review-candidate example

```yaml
parameters: []
```

## Compliant example

```yaml
parameters:
  - { name: filter, in: query, schema: { type: string } }
```

## Detection and scope

The rule has `operation` scope and checks collection `GET` operations whose
path has no template parameter. It reports when no query parameter name
matches the configured filter pattern.

## Configuration and limitations

`name-pattern` selects names containing `filter`; `check: present` selects the
presence check. The rule does not define filter grammar or require a particular
filterable field set.
