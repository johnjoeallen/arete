---
id: naming
language: starlark
source: Detector.star
scopes:
  - property
  - path-parameter
  - query-parameter
  - header
  - path-segment
  - schema
parameters:
  convention:
    type: enum
    required: false
    values:
      - camelCase
      - snake_case
      - kebab-case
      - hyphenated
  match:
    type: enum
    required: false
    values:
      - non-conforming
      - unsupported-character
      - present
  semantic:
    type: enum
    required: false
    values:
      - collection
      - singular
      - plural
  suffix:
    type: string
    required: false
  schema-type:
    type: enum
    required: false
    values:
      - array
---

# Naming detector

Evaluates names exposed by an OpenAPI contract: component schema names,
schema property names, URI path segments, and declared operation parameters.
The detector is intentionally heuristic for English singular/plural naming;
it is a policy aid, not a linguistic authority.

The detector supplies separate scopes so a rule expresses exactly which part of
the contract it is evaluating. It reports facts and does not make policy or
scoring decisions.
