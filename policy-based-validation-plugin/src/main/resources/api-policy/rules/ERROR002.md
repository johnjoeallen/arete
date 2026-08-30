---
id: ERROR002
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: client-error }
---

# ERROR002 — Operation lacks a client-error response

## Intent

Operations should document at least one `4xx` response for invalid requests,
missing resources, or other client-side failures.

## Review-candidate example

```yaml
responses:
  '200': { description: OK }
```

## Compliant example

```yaml
responses:
  '400': { description: Invalid request }
```

## Detection and scope

The rule has `operation` scope and reports when no response status is in the
`4xx` range.

## Configuration and limitations

`required-class: client-error` selects this check. The rule does not require a
specific client-error status or infer errors from response descriptions.
