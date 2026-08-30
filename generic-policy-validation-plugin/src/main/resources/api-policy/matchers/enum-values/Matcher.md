---
id: enum-values
language: distill
source: Matcher.dsl
scopes: [property]
parameters:
  check:
    type: enum
    required: true
    values: [no-duplicates]
---

# Enum-values rule

- `no-duplicates` — reports a schema property whose `enum` list repeats a
  value.
