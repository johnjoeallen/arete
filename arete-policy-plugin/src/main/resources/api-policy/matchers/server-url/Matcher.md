---
id: server-url
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [internal-host, placeholder-host, trailing-slash, url-pattern]
  pattern:
    type: string
    required: false
---

# Server-url rule

- `internal-host` — reports a declared server URL whose host looks internal or
  non-routable: `localhost`, a loopback or RFC 1918 address, a single-label
  hostname, or an internal-style suffix (`.internal`, `.local`, `.corp`,
  `.intranet`, `.lan`).
- `placeholder-host` — reports a declared server URL whose host is
  `example.com` / `example.org` / `example.net` (or a subdomain of one), i.e.
  a documentation placeholder left in a spec presented as real.
- `trailing-slash` — reports a declared server URL that ends with `/`, which
  produces a doubled slash when the client joins it with a path.
- `url-pattern` — reports a declared server URL that does not fully match
  `pattern` (RE2 syntax). A policy sets `pattern` to the organisation's
  approved production / sandbox URL shape.
