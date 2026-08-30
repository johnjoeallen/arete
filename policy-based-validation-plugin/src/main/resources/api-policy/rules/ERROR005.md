---
id: ERROR005
category: Error responses
matcher: error-response
scope: response
parameters: { problem-json: true }
---

# ERROR005 — Error response does not declare Problem Details

Error responses should offer the standard `application/problem+json`
representation.

```yaml
content:
  application/problem+json:
    schema: { $ref: '#/components/schemas/Problem' }
```
