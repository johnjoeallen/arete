---
id: STANDARD028
category: Standards
matcher: server-url
scope: api
parameters: { check: placeholder-host }
---

# STANDARD028 — Server URL uses a placeholder host

## Intent

`example.com`, `example.org`, and `example.net` are reserved for
documentation. A `servers` entry still pointing at one of them in a spec
that is otherwise presented as a real, callable API usually means a template
was never filled in — clients that trust the `servers` list will send
requests nowhere.

## Detection and scope

The rule has `api` scope and uses the `server-url` matcher with
`check: placeholder-host`. Each `servers[].url` whose host is `example.com` /
`example.org` / `example.net`, or a subdomain of one, is reported at
`/servers`.

## Diagnostic

```yaml
servers:
  - url: https://api.example.com/v1
```

## Compliant

```yaml
servers:
  - url: https://api.acme-payments.com/v1
```

## Configuration and limitations

`check: placeholder-host` is the rule's only mode. This rule is **not part of
the Enterprise Grade policy**, because `example.com` is a legitimate and
common choice for a sample or teaching spec; enable it only in a policy that
lints specs expected to be production-ready. It is a host-name check and does
not test reachability.
