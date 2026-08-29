---
id: proprietary-header
language: groovy
source: Detector.groovy
scopes: [header]
parameters:
  allowed:
    type: string
    required: true
---

# Proprietary-header detector

Finds declared proprietary headers that are not in the configured
comma-separated allow-list. Header names are compared case-insensitively.
Standard headers are ignored; the detector reports request header parameters
and response headers from the stable OpenAPI model.
