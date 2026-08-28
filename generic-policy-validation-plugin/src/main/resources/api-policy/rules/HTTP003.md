---
id: HTTP003
category: HTTP
detector: operation-semantics
scope: operation
parameters: { method: PUT, match: partial-update }
---

# HTTP003 — PUT appears to perform a partial modification

PUT should normally represent complete replacement of the target resource. This rule is a summary/path heuristic.
