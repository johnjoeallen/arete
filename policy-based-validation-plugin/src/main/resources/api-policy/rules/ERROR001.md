---
id: ERROR001
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: success }
---

# ERROR001 — Operation lacks a success response

Every operation should document at least one successful HTTP response.

```yaml
responses:
  '200': { description: OK }
```

The check is contract-only; it does not prescribe a single success status.
