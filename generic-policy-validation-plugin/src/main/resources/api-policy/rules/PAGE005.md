---
id: PAGE005
category: Pagination
matcher: pagination
scope: response
parameters: { name-pattern: ".*", check: link }
---

# PAGE005 — Paginated response lacks navigation links

Successful collection responses should expose a `Link` header for navigating
to related pages.

```yaml
headers:
  Link: { schema: { type: string } }
```
