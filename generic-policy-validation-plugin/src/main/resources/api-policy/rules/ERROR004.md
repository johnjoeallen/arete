---
id: ERROR004
category: Error responses
detector: error-response
scope: response
parameters: { require-description: true }
---

# ERROR004 — Error response lacks a description

Every documented `4xx` or `5xx` response should explain the failure in its
OpenAPI description.

```yaml
responses:
  '400':
    description: Invalid request
```
