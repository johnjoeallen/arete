---
id: ERROR003
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: server-error }
---

# ERROR003 — Operation lacks a server-error response

Operations should document at least one `5xx` response so clients know how a
service failure is represented.

```yaml
responses:
  '500': { description: Service failure }
```
