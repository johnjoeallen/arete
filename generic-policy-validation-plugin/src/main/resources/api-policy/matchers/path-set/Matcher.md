---
id: path-set
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [unique]
---

# Path-set rule

- `unique` — reports a path that is structurally identical to another once
  template parameters are normalised (`/pets/{id}` and `/pets/{petId}` collide).
