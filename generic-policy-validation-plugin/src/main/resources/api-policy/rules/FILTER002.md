---
id: FILTER002
category: Collection capabilities
detector: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])filter([-_]|$)", check: string }
---

# FILTER002 — Filter parameter is not a string expression

Filter expressions should use a string representation unless a policy
explicitly defines another encoding.
