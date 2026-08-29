---
id: STATUS002
category: HTTP status
detector: response-header
scope: response
parameters: { status: 201, header: Location, required: true }
---

# STATUS002 — Created resource response lacks location information

## Intent

When a request creates an addressable resource, a `201 Created` response can
tell the client where that resource lives through the `Location` header. This
supports follow-up retrieval and avoids making clients reconstruct an URI.
The rule is a policy check and cannot determine whether the created resource
is actually addressable.

## Detection and scope

The rule has `response` scope and uses the `response-header` detector:

```yaml
parameters: { status: 201, header: Location, required: true }
```

For every documented 201 response, the detector compares response header names
case-insensitively. It reports an occurrence when `Location` is absent, at the
containing operation pointer, with a message that the response is missing the
required header. Header values are not inspected.

## Review-candidate example

This 201 response is reported:

```yaml
paths:
  /customers:
    post:
      responses:
        '201':
          description: Customer created
          headers:
            X-Request-ID: { schema: { type: string } }
```

If the resource is addressable, the response could instead include:

```http
HTTP/1.1 201 Created
Location: https://api.example.test/customers/123
```

## Compliant example

Declaring the header satisfies this rule:

```yaml
responses:
  '201':
    description: Customer created
    headers:
      Location:
        description: URI of the newly created customer
        schema: { type: string, format: uri }
```

## Parameters, references, and limitations

The detector supports a required header or an unexpected-header mode, but this
rule fixes `status: 201`, `header: Location`, and `required: true`. It does
not validate the URI, header value, response body, creation semantics, or
whether 201 is the appropriate status. Referenced responses count only if the
host resolves their headers into the normalised response model. Runtime
headers, gateway behavior, and headers mentioned only in prose or examples are
not inspected.
