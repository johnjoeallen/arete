---
id: ERROR010
category: Authentication errors
matcher: authentication-error
scope: response
parameters: { status: 403, forbidden-header: WWW-Authenticate }
---

# ERROR010 — Authorization failure must not issue an authentication challenge

A `403 Forbidden` response indicates that authentication succeeded but the
client is not authorized. It should not include `WWW-Authenticate`, which is
reserved for authentication challenges.

```yaml
headers:
  X-Reason: { schema: { type: string } }
```
