---
id: ERROR011
category: Error responses
matcher: response-example
scope: operation
parameters: { check: unique-error-payloads }
---

# ERROR011 — Error responses share an example payload

When an operation's error responses (`4xx` / `5xx`) all show the same example
body, the documentation gives a caller no way to tell the failure modes
apart. Each error response should illustrate its own case.

## Diagnostic

```yaml
responses:
  '400':
    content: { application/problem+json: { example: { title: "Error", status: 400 } } }
  '404':
    content: { application/problem+json: { example: { title: "Error", status: 400 } } }
```

## Compliant

```yaml
responses:
  '400':
    content: { application/problem+json: { example: { title: "Invalid request", status: 400 } } }
  '404':
    content: { application/problem+json: { example: { title: "Not found", status: 404 } } }
```

## Detection and scope

The rule has `operation` scope and uses the `response-example` rule with
`check: unique-error-payloads`. Within each operation, the example bodies of
`4xx` / `5xx` responses are compared; a pair with identical examples is
reported.

## Configuration and limitations

Comparison is on the stringified example. Responses with no example are
ignored. The check is per operation, not across the whole document.
