---
id: resource-path
language: distill
source: Matcher.dsl
scopes:
  - path
  - operation
parameters:
  match:
    type: enum
    required: true
    values:
      - operation-verb
      - query-predicate
      - rpc-style
      - custom-action
      - action-style
      - trailing-slash
      - embedded-identifier
---

# Resource-path rule

Detects action-oriented resource paths. The rule receives a stable map
containing `api.paths` and a single declarative rule. It returns diagnostic
maps for affected operations; it never receives a policy or calculates a
score.
