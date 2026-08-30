---
id: FILTER001
category: Collection capabilities
matcher: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])filter([-_]|$)", check: present }
---

# FILTER001 — Collection lacks a filter capability

Collection `GET` operations should expose a documented filter query parameter
when clients need to select a subset of resources.

```yaml
parameters:
  - { name: filter, in: query, schema: { type: string } }
```
