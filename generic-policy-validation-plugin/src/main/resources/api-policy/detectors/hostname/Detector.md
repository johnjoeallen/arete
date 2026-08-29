---
id: hostname
language: groovy
source: Detector.groovy
scopes: [api]
parameters:
  convention:
    type: enum
    required: true
    values: [lowercase-hyphenated]
---

# Hostname detector

Checks the host portions of declared OpenAPI server URLs.
