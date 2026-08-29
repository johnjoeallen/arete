---
id: extensions
language: starlark
source: Detector.star
scopes: [api]
parameters:
  allowed:
    type: string
    required: false
---

# Extensions detector

Reports any `x-` specification extension whose key is not in the
comma-separated `allowed` list. It scans `info`, every operation, every
component schema and its properties, and every parameter.
