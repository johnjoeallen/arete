---
id: BULK001
category: Bulk operations
detector: bulk-operation
scope: operation
parameters:
  operation-type: create
  expected-method: POST
  payload: collection
---

# BULK001 — Bulk creation is not represented as POST of a collection

Bulk creation should normally POST a collection of entities to an appropriate collection resource. The detector flags create-like operations that do not use the configured method and collection-shaped path.
