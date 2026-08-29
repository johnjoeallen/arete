---
id: STANDARD008
category: Standards
detector: proprietary-header
scope: header
parameters: { allowed: "X-Request-Id,X-Correlation-Id" }
---

# STANDARD008 — Proprietary header is not allow-listed

Proprietary HTTP headers should be limited to the headers explicitly allowed
by the API policy. The detector checks declared request header parameters and
response headers, compares names case-insensitively, and ignores standard HTTP
headers.

The `allowed` parameter is a comma-separated list of permitted proprietary
header names. This is a contract check only; it does not inspect runtime
traffic or decide whether a header is registered by an external standards
body.

## Violation

```yaml
paths:
  /customers:
    get:
      parameters:
        - in: header
          name: X-Internal-Trace
          schema: { type: string }
```

## Compliant

```yaml
paths:
  /customers:
    get:
      parameters:
        - in: header
          name: X-Request-Id
          schema: { type: string }
```
