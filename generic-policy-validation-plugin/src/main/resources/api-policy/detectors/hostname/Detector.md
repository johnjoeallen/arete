---
id: hostname
language: starlark
source: Detector.star
scopes: [api]
parameters:
  convention:
    type: enum
    required: true
    values: [lowercase-hyphenated]
---

# Hostname detector

Checks the host portions of declared OpenAPI server URLs.
