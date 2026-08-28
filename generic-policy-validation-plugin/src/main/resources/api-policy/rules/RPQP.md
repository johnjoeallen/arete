---
id: RPQP
category: Resource design
detector: resource-path
scope: path
parameters:
  match: query-predicate
---

# RPQP — Resource path contains a query predicate

Resource paths should identify resources, not encode a query predicate.
Prefer a collection resource with a query parameter to paths such as
`/pet/findByStatus`.
