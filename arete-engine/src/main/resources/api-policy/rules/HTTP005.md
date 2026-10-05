---
id: HTTP005
category: HTTP
matcher: operation
scope: operation
parameters: { method: GET, request-body: present }
---

# HTTP005 — GET operation has a request body

## Intent

GET request bodies should normally be avoided because their semantics and
interoperability are poorly defined across clients, caches, proxies, and
servers. Query parameters or another documented resource should usually carry
selection criteria.

## Detection and scope

The rule has `operation` scope and uses the `operation` rule:

```yaml
parameters: { method: GET, request-body: present }
```

It reports every GET operation whose normalised `requestBodyPresent` fact is
true. The diagnostic points to the operation and says `GET operation has a
request body`. The rule does not inspect the body’s schema or media type.

## Review-candidate example

This GET is reported:

```yaml
paths:
  /customers/search:
    get:
      requestBody:
        content:
          application/json: { schema: { type: object } }
      responses: { '200': { description: Search results } }
```

Consider query parameters, POST-based search, or another explicit resource
design depending on the operation’s requirements.

## Compliant example

This GET has no request body and does not match:

```yaml
paths:
  /customers:
    get:
      parameters:
        - in: query
          name: name
          schema: { type: string }
      responses: { '200': { description: Customer collection } }
```

## Parameters, references, and limitations

Both configured parameters must match. The rule checks only HTTP method and
the host’s boolean request-body fact; referenced bodies count only when the
host resolves them. It does not judge runtime support, body content, query
equivalence, caching headers, or custom client behavior. Findings are policy
review candidates rather than a claim that every GET body will fail.
