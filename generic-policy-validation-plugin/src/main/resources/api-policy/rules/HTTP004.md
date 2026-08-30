---
id: HTTP004
category: HTTP
matcher: operation
scope: operation
parameters: { method: DELETE, request-body: present }
---

# HTTP004 — DELETE operation has a request body

## Intent

DELETE should normally identify its target resource through the URI rather than
depend on a request body. Request bodies on DELETE have inconsistent support
across clients, proxies, and servers, though a domain may have a documented
reason to use one.

## Detection and scope

The rule has `operation` scope and uses the `operation` rule:

```yaml
parameters: { method: DELETE, request-body: present }
```

It reports every DELETE operation whose normalised request-body-present fact
is true. The diagnostic points to the operation and says `DELETE operation has
a request body`. No body schema, media type, path, or summary is inspected.

## Review-candidate example

```yaml
paths:
  /customers/{customer_id}:
    delete:
      requestBody:
        content:
          application/json: { schema: { type: object } }
      responses: { '204': { description: Deleted } }
```

Consider representing deletion options as query parameters or a dedicated
resource if they are part of the public contract.

## Compliant example

```yaml
paths:
  /customers/{customer_id}:
    delete:
      responses: { '204': { description: Deleted } }
```

## Parameters, references, and limitations

Both configured parameters must match. Referenced request bodies count only
when resolved by the host. The rule does not inspect runtime behavior,
body contents, authorization, idempotency, or interoperability; the result is
a policy review candidate.
