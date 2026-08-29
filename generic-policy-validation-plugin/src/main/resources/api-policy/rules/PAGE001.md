---
id: PAGE001
category: Pagination
detector: pagination
scope: operation
parameters: { name-pattern: "(^|[-_])(page|offset|cursor)([-_]|$)", check: present }
---

# PAGE001 — Collection lacks a pagination control

Collection `GET` operations should document a page, offset, or cursor query
parameter when their result set can grow.

```yaml
parameters:
  - { name: page, in: query, schema: { type: integer } }
```
