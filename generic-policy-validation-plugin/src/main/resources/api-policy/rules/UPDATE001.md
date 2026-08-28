---
id: UPDATE001
category: Update semantics
detector: operation-semantics
scope: operation
parameters:
  method: PUT
  match: partial-update
---

# UPDATE001 — PUT appears to perform a partial update

PUT should normally replace the complete representation of an identified resource. This heuristic flags documentation or paths that describe a partial update.
