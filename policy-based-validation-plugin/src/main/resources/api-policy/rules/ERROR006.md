---
id: ERROR006
category: Error responses
matcher: error-response
scope: response
parameters: { status: 401, required-header: WWW-Authenticate }
---

# ERROR006 — Unauthorized response lacks the authentication challenge

A `401 Unauthorized` response should include `WWW-Authenticate` so a client
can determine how to authenticate.

```yaml
headers:
  WWW-Authenticate: { schema: { type: string } }
```
