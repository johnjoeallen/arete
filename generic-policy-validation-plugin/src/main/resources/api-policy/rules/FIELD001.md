---
id: FIELD001
category: Collection capabilities
detector: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])(fields|select)([-_]|$)", check: present }
---

# FIELD001 — Collection lacks a field-selection capability

Collection operations may expose a field-selection query parameter so clients
can request only the representation fields they need.

```yaml
parameters:
  - { name: fields, in: query, schema: { type: string } }
```
