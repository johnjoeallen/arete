---
id: STATUS007
category: HTTP status
detector: response-header
scope: response
parameters: { status: 429, headers: "RateLimit-Limit,RateLimit-Remaining,RateLimit-Reset", required: true }
---

# STATUS007 — Rate-limit response lacks required headers

A `429 Too Many Requests` response should describe the applicable rate limit
using the headers configured by the active policy. Header names are matched
case-insensitively and every configured header is required.

## Violation

```yaml
responses:
  '429':
    description: Too many requests
    headers:
      RateLimit-Limit: { schema: { type: integer } }
```

## Compliant

```yaml
responses:
  '429':
    description: Too many requests
    headers:
      RateLimit-Limit: { schema: { type: integer } }
      RateLimit-Remaining: { schema: { type: integer } }
      RateLimit-Reset: { schema: { type: integer } }
```

The configured header list may be overridden per policy. The detector checks
the documented OpenAPI response only and does not inspect runtime headers.
