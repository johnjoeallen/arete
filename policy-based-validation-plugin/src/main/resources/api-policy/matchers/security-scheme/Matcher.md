---
id: security-scheme
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [defined]
---

# Security-scheme rule

- `defined` — reports a `security` requirement (global or on an operation)
  that names a scheme which is not declared under
  `components.securitySchemes`. A requirement pointing at an undefined scheme
  is silently ignored by most tooling, so the operation ends up unsecured
  without any obvious sign.

One occurrence is emitted per undefined scheme name per requirement site.
