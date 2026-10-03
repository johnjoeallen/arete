---
id: STATUS008
category: HTTP status
matcher: status-class
scope: response
parameters: { forbidden: server-error }
---

# STATUS008 — Server-error response is documented

## Intent

Some API programmes require `5xx` responses to be left out of the published
contract: a server failure is not part of the interface a client codes
against, and documenting it invites clients to treat it as a normal outcome.

## Diagnostic

```yaml
responses:
  '200': { description: OK }
  '500': { description: Internal server error }
```

## Compliant

```yaml
responses:
  '200': { description: OK }
  '400': { description: Invalid request }
```

## Detection and scope

The rule has `response` scope and uses the `status-class` rule with
`forbidden: server-error`. Every documented response whose status is in the
`500`–`599` range is reported.

## Configuration and limitations

This rule is **mutually exclusive with ERROR003** ("Operation lacks a
server-error response"): a policy should enable one or the other, never both.
ERROR003 is not part of the default Enterprise Grade policy for this reason.
