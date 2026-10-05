---
id: ERROR008
category: Authentication errors
matcher: authentication-error
scope: operation
parameters: { required-status: 401 }
---

# ERROR008 — Secured operation lacks an authentication failure response

## Intent

A secured operation should document `401 Unauthorized` for requests that do
not provide valid authentication.

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
  '401': { description: Authentication required }
```

## Detection and scope

The rule has `operation` scope and reports a secured operation with no `401`
response. Operation security overrides the document-level security requirement
when it is present.

## Configuration and limitations

`required-status: 401` selects this check. It does not prove that the security
scheme works or that the response body follows a particular error format.
