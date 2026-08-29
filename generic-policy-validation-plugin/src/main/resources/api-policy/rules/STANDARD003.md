---
id: STANDARD003
category: Standards
detector: response-header
scope: response
parameters: { status: 200, header: Link, required: false }
---

# STANDARD003 — Response contains a Link header

## Intent

JSON API responses should not use HTTP `Link` headers for application-level
relationships when those relationships belong in the representation or an
explicit application contract. Keeping relationship data in one documented
place can make clients and caches more predictable. This is a policy
convention, not a prohibition on every legitimate use of Web Linking.

## Detection and scope

The rule has `response` scope and uses the `response-header` detector:

```yaml
parameters: { status: 200, header: Link, required: false }
```

For each documented 200 response, the detector checks response header names
case-insensitively. Because `required: false`, it reports when `Link` is
present, at the operation pointer, with a message that the response contains
an unexpected header. Header values are not inspected.

## Review-candidate example

This response is reported:

```yaml
paths:
  /customers:
    get:
      responses:
        '200':
          description: Customer collection
          headers:
            Link:
              description: Related customer resources
              schema: { type: string }
```

If the header is an intentional standards-based web link, the team should
record that exception. Otherwise, represent the relationship in the JSON
body and document its schema.

## Compliant example

This 200 response declares no Link header and does not match:

```yaml
responses:
  '200':
    description: Customer collection
    content:
      application/json:
        schema:
          type: object
          properties:
            customers: { type: array }
            related_orders: { type: string, format: uri }
```

## Parameters, references, and limitations

The rule is fixed to status 200 and a case-insensitive `Link` name. It does
not inspect header values, response bodies, other status codes, runtime
headers, or whether a Link is used for standards-compliant navigation rather
than application relationships. Referenced responses count only when the
host resolves their headers into the normalised model. Findings are policy
review candidates.
