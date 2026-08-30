---
id: ERROR002
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: client-error }
---

# ERROR002 — Operation lacks a client-error response

Operations should document at least one `4xx` response for invalid requests,
missing resources, or other client-side failures.

```yaml
responses:
  '400': { description: Invalid request }
```
