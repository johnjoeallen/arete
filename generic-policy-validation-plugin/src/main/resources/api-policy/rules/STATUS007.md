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

## Detection and scope

The rule has `response` scope and uses the `response-header` detector. Its
default parameters require all three headers on every documented 429 response:

```yaml
status: 429
headers: "RateLimit-Limit,RateLimit-Remaining,RateLimit-Reset"
required: true
```

Header names are compared case-insensitively. A 429 response missing one or
more configured headers produces an occurrence at the containing operation
pointer. Other response statuses are ignored.

## Configuration and limitations

Policies may override the comma-separated `headers` list, `status`, or
required mode. The detector checks only declared response headers and does not
validate values, units, reset-time format, descriptions, or whether the
headers are emitted at runtime. Referenced responses count only after host
normalisation. The rule does not require a particular quota algorithm.
