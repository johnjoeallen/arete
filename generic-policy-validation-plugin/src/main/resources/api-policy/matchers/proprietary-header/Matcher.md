---
id: proprietary-header
language: distill
source: Matcher.dsl
scopes: [header]
parameters:
  allowed:
    type: string
    required: true
---

# Proprietary-header rule

Finds declared proprietary headers that are not in the configured
comma-separated allow-list. Header names are compared case-insensitively.
Standard headers are ignored; the rule reports request header parameters
and response headers from the stable OpenAPI model.
