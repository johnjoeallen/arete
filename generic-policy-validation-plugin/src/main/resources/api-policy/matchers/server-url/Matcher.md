---
id: server-url
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [internal-host, url-pattern]
  pattern:
    type: string
    required: false
---

# Server-url rule

- `internal-host` — reports a declared server URL whose host looks internal or
  non-routable: `localhost`, a loopback or RFC 1918 address, a single-label
  hostname, or an internal-style suffix (`.internal`, `.local`, `.corp`,
  `.intranet`, `.lan`).
- `url-pattern` — reports a declared server URL that does not fully match
  `pattern` (RE2 syntax). A policy sets `pattern` to the organisation's
  approved production / sandbox URL shape.
