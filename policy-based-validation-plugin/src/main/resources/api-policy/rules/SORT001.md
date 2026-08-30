---
id: SORT001
category: Collection capabilities
matcher: collection-capability
scope: operation
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: present }
---

# SORT001 — Collection lacks a sort capability

Collection `GET` operations should document how clients request a stable sort
order.

```yaml
parameters:
  - { name: sort, in: query, schema: { type: string } }
```
