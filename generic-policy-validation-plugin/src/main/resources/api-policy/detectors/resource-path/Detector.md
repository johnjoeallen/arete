---
id: resource-path
language: groovy
source: Detector.groovy
scopes:
  - path
parameters:
  match:
    type: enum
    required: true
    values:
      - operation-verb
      - query-predicate
---

# Resource-path detector

Detects action-oriented resource paths. The detector receives a stable map
containing `api.paths` and a single declarative rule. It returns occurrence
maps for affected operations; it never receives a policy or calculates a
score.
