---
id: ERROR007
category: Error responses
matcher: error-response
scope: response
parameters: { status: 405, required-header: Allow }
---

# ERROR007 — Method-not-allowed response lacks Allow

A `405 Method Not Allowed` response should list the supported methods in an
`Allow` header.

```yaml
headers:
  Allow: { schema: { type: string } }
```
