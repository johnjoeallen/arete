---
id: metadata
language: groovy
source: Detector.groovy
scopes: [api]
parameters:
  required:
    type: enum
    required: true
    values: [complete]
---

# Metadata detector

Checks the documented OpenAPI information fields. A complete metadata record
has a title, description, contact name and email, and a semantic version.
