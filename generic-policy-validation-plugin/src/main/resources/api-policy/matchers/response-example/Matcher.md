---
id: response-example
language: distill
source: Matcher.dsl
scopes: [operation]
parameters:
  check:
    type: enum
    required: true
    values: [unique-error-payloads]
---

# Response-example rule

- `unique-error-payloads` — reports an operation whose `4xx` / `5xx`
  responses declare identical example payloads, so a caller cannot tell the
  failure modes apart from the documentation.
