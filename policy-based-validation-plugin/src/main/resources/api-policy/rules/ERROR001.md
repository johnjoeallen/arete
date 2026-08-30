---
id: ERROR001
category: Error responses
matcher: error-response
scope: operation
parameters: { required-class: success }
---

# ERROR001 — Operation lacks a success response

## Intent

Every operation should document at least one successful HTTP response.

## Review-candidate example

This operation has only an error response and is reported:

```yaml
responses:
  '400': { description: Invalid request }
```

## Compliant example

```yaml
responses:
  '200': { description: OK }
```

## Detection and scope

The rule has `operation` scope and reports when no response status is in the
`2xx` range. It does not prescribe a particular success status.

## Configuration and limitations

`required-class: success` selects this check. The rule inspects documented
status keys only; it does not infer success from descriptions or runtime
responses.

The check is contract-only; it does not prescribe a single success status.
