---
id: path-count
language: groovy
source: Detector.groovy
scopes: [api]
parameters:
  maximum:
    type: integer
    required: true
  maximum-depth:
    type: integer
    required: false
---

# Path-count detector

Counts distinct first path segments representing top-level resource types.
It can also flag resource paths exceeding a configured nesting depth.
