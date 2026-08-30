---
id: STANDARD008
category: Standards
matcher: proprietary-header
scope: header
parameters: { allowed: "X-Request-Id,X-Correlation-Id" }
---

# STANDARD008 — Proprietary header is not allow-listed

Proprietary HTTP headers should be limited to the headers explicitly allowed
by the API policy. The rule checks declared request header parameters and
response headers, compares names case-insensitively, and ignores standard HTTP
headers.

The `allowed` parameter is a comma-separated list of permitted proprietary
header names. This is a contract check only; it does not inspect runtime
traffic or decide whether a header is registered by an external standards
body.

## Diagnostic

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

## Detection and scope

The rule has `header` scope and uses the `proprietary-header` rule. It
checks request header parameters and response header declarations. A header is
considered proprietary when its case-insensitive name is not in the rule’s
standard-header list and begins with `X-` or `X_`. Such a header is reported
unless its lowercased name occurs in the comma-separated `allowed` list.

## Configuration and limitations

`allowed` defaults to `X-Request-Id,X-Correlation-Id` and can be overridden per
policy. Matching is case-insensitive and applies separately to request and
response declarations. The rule does not inspect ordinary non-`X-`
extension names, runtime traffic, header values, registration status, or
descriptions. Referenced operations and responses count only after host
normalisation. Findings are allow-list policy candidates, not proof that a
header is unsafe.
