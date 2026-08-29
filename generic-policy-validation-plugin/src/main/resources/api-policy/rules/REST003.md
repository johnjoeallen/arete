---
id: REST003
category: Resource design
detector: resource-path
scope: operation
parameters: { match: rpc-style }
---

# REST003 — API uses RPC-style resource design

## Intent

HTTP APIs should normally model resources rather than expose each operation as
a remote procedure call. Resource-oriented paths let standard HTTP methods
carry common semantics and make related endpoints easier to discover. Some
domain commands are legitimately action-like, so this rule is a policy review
candidate.

## Detection and scope

The rule has `operation` scope and uses the `resource-path` detector:

```yaml
parameters: { match: rpc-style }
```

The detector tokenizes the path by `/`. A path matches when it has more than
one token and its terminal segment, compared case-insensitively, is exactly one
of `get`, `list`, `create`, `update`, `delete`, `remove`, `add`, or `set`.
Every operation on a matching path is reported at its operation pointer with
`API uses RPC-style resource design`.

## Review-candidate example

This path ends in the RPC-style verb `get` and is reported:

```yaml
paths:
  /customers/get:
    get:
      summary: Get customers
      responses: { '200': { description: Customer collection } }
```

Consider a resource path such as `/customers`, using GET to retrieve the
collection, or `/customers/{customer_id}` for one customer.

## Compliant example

The terminal segment is a resource name rather than one of the configured
verbs, so this path does not match:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

## Parameters, references, and limitations

`match: rpc-style` selects the fixed verb list. The rule does not inspect the
HTTP method, summary, schemas, request/response data, or runtime behavior; a
verb-looking path may still be an intentional resource name. The detector’s
tokenization and exact terminal comparison mean verbs embedded in longer
segments, such as `/getCustomers`, are not matched by REST003 (REST001 covers
operation-verb prefixes). Unresolved path references may omit evidence.
