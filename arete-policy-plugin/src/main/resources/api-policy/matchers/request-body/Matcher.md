---
id: request-body
language: distill
source: Matcher.dsl
scopes: [operation]
parameters:
  check:
    type: enum
    required: true
    values: [forbidden-on-methods, required-flag-missing]
  methods:
    type: string
    required: false
---

# Request-body rule

Inspects operation request bodies.

- `forbidden-on-methods` — reports an operation whose method is in the
  comma-separated `methods` list but that still declares a request body.
- `required-flag-missing` — reports an operation that declares a request body
  without `required: true`.
