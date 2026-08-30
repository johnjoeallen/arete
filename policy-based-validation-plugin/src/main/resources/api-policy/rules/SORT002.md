---
id: SORT002
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: string }
---

# SORT002 — Sort parameter is not a string expression

Sort fields and directions should be represented as a string expression that
can be extended without changing the parameter type.
