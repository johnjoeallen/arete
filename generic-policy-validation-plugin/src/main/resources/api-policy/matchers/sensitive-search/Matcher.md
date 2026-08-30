---
id: sensitive-search
language: distill
source: Matcher.dsl
scopes: [query-parameter, operation]
parameters:
  search-pattern:
    type: string
    required: true
  sensitive-pattern:
    type: string
    required: true
---

# Sensitive-search rule

Identifies search inputs and operations that allow sensitive fields to be
searched. Both regular expressions are policy-controlled so a policy can
define its own search vocabulary and sensitive data inventory.
