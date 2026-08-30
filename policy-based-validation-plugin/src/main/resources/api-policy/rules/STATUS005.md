---
id: STATUS005
category: HTTP status
matcher: response-code
scope: response
parameters: { match: semantic-conflict }
---

# STATUS005 — Status code conflicts with operation semantics

## Intent

HTTP status codes should communicate the outcome clients can rely on. A
successful 2xx code paired with an explicit error description can produce
ambiguous client behavior and misleading monitoring. This initial rule is a
narrow documentation heuristic and reports candidates for review, not proven
runtime contradictions.

## Detection and scope

The rule has `response` scope and uses the `response-code` rule with:

```yaml
parameters: { match: semantic-conflict }
```

It examines documented 2xx responses and checks their descriptions for
case-insensitive error wording: `error`, `failure`, `failed`, or `invalid`.
Matching descriptions produce an diagnostic at the operation pointer with the
message `Status code conflicts with response semantics`. The rule does not
inspect response bodies or infer semantics from the HTTP method.

## Review-candidate example

This response is reported because a 200 description contains “error”:

```yaml
paths:
  /customers:
    get:
      responses:
        '200':
          description: Error loading customer list
          content:
            application/json: { schema: { type: object } }
```

The reviewer should decide whether the response should use an error status,
or whether the description is inaccurate and should describe the successful
result instead.

## Compliant example

This 200 response has no explicit conflict wording and does not match:

```yaml
paths:
  /customers:
    get:
      responses:
        '200':
          description: Customer list returned
```

A documented 400 response with an error description is also outside this
rule’s 2xx-only check.

## Parameters, references, and limitations

`match: semantic-conflict` enables the check; the rule has no status parameter.
Referenced responses are evaluated only if the host resolves them into the
normalised operation model. The heuristic depends on the response description
and can miss misleading wording that uses other terms or flag a legitimate
description that mentions an error condition. It does not inspect runtime
outcomes, examples, headers, schemas, response links, or whether the status
code is otherwise appropriate.
