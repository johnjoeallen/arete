---
id: document-lint
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [parser-message, numeric-status-key]
  pattern:
    type: string
    required: false
---

# Document-lint rule

Reads the `lint` block the host attaches from parser diagnostics and the raw
document text.

- `parser-message` — reports every parser message matching `pattern` (RE2
  syntax); used to surface unresolved `$ref` errors.
- `numeric-status-key` — reports when the raw document declares HTTP status
  keys as bare numbers (`200:`) rather than strings (`'200':`).
