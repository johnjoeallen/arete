---
id: schema
language: groovy
source: Detector.groovy
scopes:
  - property
parameters:
  type:
    type: enum
    required: false
    values:
      - string
      - integer
      - number
      - array
  format:
    type: enum
    required: false
    values: [absent]
  enum:
    type: enum
    required: false
    values:
      - present
      - absent
  nullable:
    type: boolean
    required: false
  required:
    type: boolean
    required: false
  semantics:
    type: enum
    required: false
    values:
      - undefined
  max-items:
    type: enum
    required: false
    values: [absent, present]
---

# Schema detector

Inspects primitive declarative facts about component-schema properties. The
first implementation deliberately limits itself to information directly
available in an OpenAPI contract: type, requiredness, explicit nullability,
and whether an enum is declared. It does not resolve references or claim to
know runtime semantics.

Rules may combine supplied parameters; all conditions must match. A
`semantics: undefined` value documents a policy concern but cannot establish
business semantics from OpenAPI alone, so it never broadens the check beyond
the objective nullable/optional conditions.
