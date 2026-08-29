---
id: collection-capability
language: starlark
source: Detector.star
scopes: [operation, query-parameter]
parameters:
  name-pattern:
    type: string
    required: true
  check:
    type: enum
    required: true
    values: [present, string, array, form]
---

# Collection-capability detector

Checks conventional query capabilities on collection `GET` operations. Rules
provide the parameter-name pattern and the contract check, allowing each
policy to choose its own vocabulary and serialization convention.
