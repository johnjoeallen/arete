---
id: server-url
language: starlark
source: Detector.star
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [internal-host]
---

# Server-url detector

- `internal-host` — reports a declared server URL whose host looks internal or
  non-routable: `localhost`, a loopback or RFC 1918 address, a single-label
  hostname, or an internal-style suffix (`.internal`, `.local`, `.corp`,
  `.intranet`, `.lan`).
