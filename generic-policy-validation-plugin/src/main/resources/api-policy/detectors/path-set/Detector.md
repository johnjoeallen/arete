---
id: path-set
language: starlark
source: Detector.star
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [unique]
---

# Path-set detector

- `unique` — reports a path that is structurally identical to another once
  template parameters are normalised (`/pets/{id}` and `/pets/{petId}` collide).
