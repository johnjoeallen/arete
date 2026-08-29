---
id: identifier
language: starlark
source: Detector.star
scopes: [property]
parameters:
  name-pattern:
    type: string
    required: true
  check:
    type: enum
    required: true
    values: [string, format]
  format:
    type: string
    required: false
---

# Identifier detector

Checks schema properties whose names identify resources. The name pattern and
the expected format are policy parameters; the detector reports contract
metadata only and does not infer identifier uniqueness at runtime.
