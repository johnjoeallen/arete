---
id: ERROR004
category: Error responses
matcher: error-response
scope: response
parameters: { require-description: true }
---

# ERROR004 — Error response lacks a description

## Intent

Every documented `4xx` or `5xx` response should explain the failure in its
OpenAPI description.

## Review-candidate example

```yaml
responses:
  '400': {}
```

## Compliant example

```yaml
responses:
  '400':
    description: Invalid request
```

## Detection and scope

The rule has `response` scope and reports an error response whose description
is absent or blank. Success responses are ignored.

## Configuration and limitations

`require-description: true` selects this check. The rule checks the OpenAPI
description field and does not infer one from a schema, status code, or example.
