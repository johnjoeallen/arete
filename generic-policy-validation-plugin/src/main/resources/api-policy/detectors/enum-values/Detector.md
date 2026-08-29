---
id: enum-values
language: starlark
source: Detector.star
scopes: [property]
parameters:
  check:
    type: enum
    required: true
    values: [no-duplicates]
---

# Enum-values detector

- `no-duplicates` — reports a schema property whose `enum` list repeats a
  value.
