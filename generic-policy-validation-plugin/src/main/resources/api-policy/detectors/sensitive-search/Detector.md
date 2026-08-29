---
id: sensitive-search
language: starlark
source: Detector.star
scopes: [query-parameter, operation]
parameters:
  search-pattern:
    type: string
    required: true
  sensitive-pattern:
    type: string
    required: true
---

# Sensitive-search detector

Identifies search inputs and operations that allow sensitive fields to be
searched. Both regular expressions are policy-controlled so a policy can
define its own search vocabulary and sensitive data inventory.
