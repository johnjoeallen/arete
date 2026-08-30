---
id: ERROR006
category: Error responses
matcher: error-response
scope: response
parameters: { status: 401, required-header: WWW-Authenticate }
---

# ERROR006 — Unauthorized response lacks the authentication challenge

## Intent

A `401 Unauthorized` response should include `WWW-Authenticate` so a client
can determine how to authenticate.

## Review-candidate example

```yaml
description: Authentication required
headers: {}
```

## Compliant example

```yaml
headers:
  WWW-Authenticate: { schema: { type: string } }
```

## Detection and scope

The rule has `response` scope and reports a `401` response without the
case-insensitive `WWW-Authenticate` header.

## Configuration and limitations

`status: 401` and `required-header: WWW-Authenticate` select this check. The
rule does not validate the challenge value or authentication scheme.
