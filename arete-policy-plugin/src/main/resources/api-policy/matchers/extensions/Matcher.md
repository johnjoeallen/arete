---
id: extensions
language: distill
source: Matcher.distill
scopes: [api]
parameters:
  allowed:
    type: string
    required: false
---

# Extensions rule

Reports any `x-` specification extension whose key is not in the
comma-separated `allowed` list. It scans `info`, every operation, every
component schema and its properties, and every parameter.
