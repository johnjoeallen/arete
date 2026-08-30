---
id: REST007
category: Resource design
matcher: path-prefix
scope: api
---

# REST007 — All paths share a common prefix

## Intent

When every path begins with the same fixed segment — a version marker or a
service name such as `/v1` or `/payments` — that segment is a base path, not
part of any resource identity. It belongs in the server URL, where it can be
changed in one place, rather than being repeated on every path.

## Detection and scope

The rule has `api` scope and uses the `path-prefix` matcher. It reports a
single finding at `/paths` when there is more than one path and every path's
first literal segment (ignoring `{template}` parameters) is identical.

## Review-candidate example

```yaml
servers:
  - url: https://api.example.com
paths:
  /v1/orders: { get: { responses: { '200': { description: OK } } } }
  /v1/customers: { get: { responses: { '200': { description: OK } } } }
```

Both paths start with `/v1`; moving it to the server URL
(`https://api.example.com/v1`) leaves `/orders` and `/customers`.

## Compliant example

```yaml
servers:
  - url: https://api.example.com/v1
paths:
  /orders: { get: { responses: { '200': { description: OK } } } }
  /customers: { get: { responses: { '200': { description: OK } } } }
```

## Limitations

The rule only fires when the shared prefix is total — one path that breaks the
pattern suppresses the finding. It compares literal segments only and takes no
position on whether the extracted prefix should be a path or a server
variable.
