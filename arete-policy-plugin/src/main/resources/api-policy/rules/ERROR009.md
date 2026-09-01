---
id: ERROR009
category: Authentication errors
matcher: authentication-error
scope: operation
parameters: { required-status: 403 }
---

# ERROR009 — Secured operation lacks an authorization failure response

## Intent

A secured operation should document `403 Forbidden` for authenticated clients
that do not have permission to perform the operation.

## Review-candidate example

```yaml
security:
  - bearerAuth: []
responses:
  '200': { description: OK }
```

## Compliant example

```yaml
responses:
  '403': { description: Insufficient permission }
```

## Detection and scope

The rule has `operation` scope and reports a secured operation with no `403`
response.

## Configuration and limitations

`required-status: 403` selects this check. It does not prove that the caller is
authenticated or that the authorization decision is implemented correctly.
