---
id: ERROR008
category: Authentication errors
detector: authentication-error
scope: operation
parameters: { required-status: 401 }
---

# ERROR008 — Secured operation lacks an authentication failure response

A secured operation should document `401 Unauthorized` for requests that do
not provide valid authentication.

```yaml
responses:
  '401': { description: Authentication required }
```
