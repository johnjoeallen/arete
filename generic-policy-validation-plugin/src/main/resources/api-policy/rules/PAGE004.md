---
id: PAGE004
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])limit([-_]|$)", check: maximum, maximum: 100 }
---

# PAGE004 — Page-size parameter lacks a safe maximum

Pagination limits should declare a maximum so a client cannot request an
unbounded page.

```yaml
schema:
  type: integer
  maximum: 100
```
