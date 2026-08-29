---
id: STATUS003
category: HTTP status
detector: response-code
scope: response
parameters: { status: 403, expected-status: 401 }
---

# STATUS003 — Authentication failure uses an inappropriate status

## Intent

HTTP 401 commonly communicates that authentication is required or has failed,
while 403 commonly communicates that the request was understood but is not
authorized. Mixing those meanings can make client recovery and security
diagnostics ambiguous. This rule flags documented 403 responses for review
where the policy expects authentication failures to use 401; it cannot infer
the actual reason a response is returned.

## Detection and scope

The rule has `response` scope and uses the `response-code` detector with:

```yaml
parameters: { status: 403, expected-status: 401 }
```

The current detector matches every response whose normalised status is 403
and reports it at the containing operation pointer with `Response uses the
configured status code`. The `expected-status: 401` value documents the
policy expectation but is not currently used by this response-matching
branch; the detector does not determine whether the 403 is an authentication
failure.

## Review-candidate example

This response is reported:

```yaml
paths:
  /customers:
    get:
      responses:
        '403':
          description: Authentication failed
```

If the request lacks valid credentials, the API may need to document 401
instead. If the caller is authenticated but lacks permission, 403 may be the
correct status and the finding can be accepted with that rationale.

## Compliant example

An operation documenting 401 without 403 does not match this rule:

```yaml
paths:
  /customers:
    get:
      responses:
        '401': { description: Authentication required }
```

This does not prove that all authorization semantics are correct.

## Parameters, references, and limitations

`status: 403` selects the response being inspected and `expected-status: 401`
is the policy metadata for the intended alternative. The detector does not
inspect response descriptions, `WWW-Authenticate`, security requirements,
runtime authentication state, or response bodies. Referenced responses count
only when the host resolves them into normalised facts. Because the current
implementation cannot distinguish authentication from authorization failures,
every documented 403 is a candidate for human review.
