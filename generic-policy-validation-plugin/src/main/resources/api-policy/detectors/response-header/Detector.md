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
    required: false
  headers:
    type: string
    required: false
  required:
    type: boolean
    required: true
---

# Response-header detector

Checks whether a documented response contains a named header or a
comma-separated list of named headers. Header matching is case-insensitive, as
required by HTTP field-name semantics. A required list requires every header;
an unexpected list reports when any configured header is present.
