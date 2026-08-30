---
id: SORT003
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])sort[-_]?fields?([-_]|$)", check: array }
---

# SORT003 — Multi-field sort parameter is not an array

When a contract allows multiple sort fields, the schema should declare an
array so the serialization is explicit.
