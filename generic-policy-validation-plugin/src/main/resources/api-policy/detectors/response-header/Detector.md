---
id: response-header
language: groovy
source: Detector.groovy
scopes: [response]
parameters:
  status:
    type: integer
    required: true
  header:
    type: string
    required: true
  required:
    type: boolean
    required: true
---

# Response-header detector

Checks whether a documented response contains a named header. Header matching
is case-insensitive, as required by HTTP field-name semantics. The detector
does not inspect runtime responses.
