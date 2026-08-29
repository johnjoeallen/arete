---
id: security
language: starlark
source: Detector.star
scopes: [operation]
parameters:
  scheme:
    type: string
    required: true
  scopes:
    type: string
    required: false
---

# Security detector

Checks that every operation requires the configured OpenAPI security scheme and,
when configured, the required comma-separated scopes.
Operation-level security overrides the document-level requirement; an
explicit empty operation security array therefore means that the operation is
intentionally anonymous and is reported. Operations inherit the document
security requirements when they do not declare their own.
