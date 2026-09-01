---
id: ERROR007
category: Error responses
matcher: error-response
scope: response
parameters: { status: 405, required-header: Allow }
---

# ERROR007 — Method-not-allowed response lacks Allow

## Intent

A `405 Method Not Allowed` response should list the supported methods in an
`Allow` header.

## Review-candidate example

```yaml
description: Method not allowed
headers: {}
```

## Compliant example

```yaml
headers:
  Allow: { schema: { type: string } }
```

## Detection and scope

The rule has `response` scope and reports a `405` response without the
case-insensitive `Allow` header.

## Configuration and limitations

`status: 405` and `required-header: Allow` select this check. The rule does not
validate that the header lists the actual operations available on the path.
