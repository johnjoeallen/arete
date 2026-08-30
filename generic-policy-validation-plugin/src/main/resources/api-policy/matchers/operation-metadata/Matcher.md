---
id: operation-metadata
language: distill
source: Matcher.dsl
scopes: [api, operation]
parameters:
  check:
    type: enum
    required: true
    values: [unique-operation-id, tags-present]
---

# Operation-metadata rule

Inspects per-operation metadata across the whole document.

- `unique-operation-id` — reports an operation with no `operationId`, and an
  `operationId` used by more than one operation.
- `tags-present` — reports an operation that is not assigned any tag.
