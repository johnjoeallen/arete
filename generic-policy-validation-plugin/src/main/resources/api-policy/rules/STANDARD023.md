---
id: STANDARD023
category: Standards
detector: server-url
scope: api
parameters: { check: url-pattern, pattern: "https://[a-z0-9-]+(\\.[a-z0-9-]+)+(:[0-9]+)?(/[^\\s]*)?" }
---

# STANDARD023 — Server URL is not on the approved pattern

A published `servers` entry should match the organisation's approved URL
shape. The default pattern only requires HTTPS and a dotted public host; an
organisation overrides `pattern` with its real production / sandbox URL
convention.

## Violation

```yaml
servers:
  - url: http://localhost:8080/v1
  - url: https://my-service/v1
```

## Compliant

```yaml
servers:
  - url: https://api.example.com/v1
```

With a policy override, e.g. `pattern: "https://(api|sandbox)\\.example\\.com/.*"`:

```yaml
servers:
  - url: https://api.example.com/payments/v1
  - url: https://sandbox.example.com/payments/v1
```

## Detection and scope

The rule has `api` scope and uses the `server-url` detector with
`check: url-pattern`. Every `servers[].url` that does not fully match
`pattern` (RE2 syntax) is reported.

## Configuration and limitations

`pattern` is a policy parameter and is expected to be overridden. This rule
is **not** part of the default Enterprise Grade policy — enable it in a
policy that carries your organisation's approved production / sandbox URL
convention.
