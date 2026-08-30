---
id: status-class
language: distill
source: Matcher.dsl
scopes: [response]
parameters:
  forbidden:
    type: enum
    required: true
    values: [server-error]
---

# Status-class rule

- `server-error` — reports any documented `5xx` response. Some API programmes
  require server-failure representations to be excluded from the published
  contract.
