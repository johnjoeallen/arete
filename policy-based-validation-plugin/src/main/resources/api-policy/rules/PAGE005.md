---
id: PAGE005
category: Pagination
matcher: pagination
scope: response
parameters: { name-pattern: ".*", check: link }
---

# PAGE005 — Paginated response lacks navigation links

## Intent

Successful collection responses should expose a `Link` header for navigating
to related pages.

## Review-candidate example

```yaml
headers: {}
```

## Compliant example

```yaml
headers:
  Link: { schema: { type: string } }
```

## Detection and scope

The rule has `response` scope and checks successful responses of collection
`GET` operations. It reports when the response has no case-insensitive `Link`
header.

## Configuration and limitations

`check: link` selects the header check. The rule does not validate Link syntax,
relation values, or whether the operation is actually paginated.
