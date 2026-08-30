---
id: ERROR009
category: Authentication errors
matcher: authentication-error
scope: operation
parameters: { required-status: 403 }
---

# ERROR009 — Secured operation lacks an authorization failure response

A secured operation should document `403 Forbidden` for authenticated clients
that do not have permission to perform the operation.

```yaml
responses:
  '403': { description: Insufficient permission }
```
