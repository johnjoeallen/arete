---
id: request-body
language: starlark
source: Detector.star
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

# Request-body detector

Inspects operation request bodies.

- `forbidden-on-methods` — reports an operation whose method is in the
  comma-separated `methods` list but that still declares a request body.
- `required-flag-missing` — reports an operation that declares a request body
  without `required: true`.
