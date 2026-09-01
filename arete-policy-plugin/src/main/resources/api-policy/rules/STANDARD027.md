---
id: STANDARD027
category: Standards
matcher: server-url
scope: api
parameters: { check: trailing-slash }
---

# STANDARD027 — Server URL has a trailing slash

## Intent

A client builds a request URL by joining a `servers[].url` with a path key
(`/orders`). If the server URL ends in `/`, the join produces `//orders`.
Some servers normalise it, some return 404, and some route it to a different
handler — so a trailing slash is a portability hazard with no upside.

## Detection and scope

The rule has `api` scope and uses the `server-url` matcher with
`check: trailing-slash`. Every `servers[].url` that ends with `/` (except the
bare relative root `/`) is reported at `/servers`.

## Diagnostic

```yaml
servers:
  - url: https://api.example.com/v1/
```

## Compliant

```yaml
servers:
  - url: https://api.example.com/v1
```

## Configuration and limitations

`check: trailing-slash` is the rule's only mode. It is a literal check on the
declared URL string and does not resolve server variables — a URL whose
trailing slash comes from a variable default is not evaluated.
