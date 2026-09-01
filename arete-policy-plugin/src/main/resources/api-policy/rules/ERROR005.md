---
id: ERROR005
category: Error responses
matcher: error-response
scope: response
parameters: { problem-json: true }
---

# ERROR005 — Error response does not declare Problem Details

## Intent

Error responses should offer the standard `application/problem+json`
representation.

## Review-candidate example

```yaml
content:
  application/json:
    schema: { type: object }
```

## Compliant example

```yaml
content:
  application/problem+json:
    schema: { $ref: '#/components/schemas/Problem' }
```

## Detection and scope

The rule has `response` scope and reports `4xx` and `5xx` responses that do not
declare `application/problem+json`.

## Configuration and limitations

`problem-json: true` selects this check. The rule checks documented media type
keys and does not validate the referenced schema's shape or runtime payload.
