---
id: ERROR003
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: server-error }
---

# ERROR003 — Operation lacks a server-error response

## Intent

Operations should document at least one `5xx` response so clients know how a
service failure is represented.

## Review-candidate example

```yaml
responses:
  '200': { description: OK }
```

## Compliant example

```yaml
responses:
  '500': { description: Service failure }
```

## Detection and scope

The rule has `operation` scope and reports when no response status is in the
`5xx` range.

## Configuration and limitations

`required-class: server-error` selects this check. The rule does not require a
specific server-error status or infer errors from response descriptions.
