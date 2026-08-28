---
id: HTTP001
category: HTTP
detector: operation-semantics
scope: operation
parameters: { method: GET, expected: safe }
---

# HTTP001 — GET operation appears to mutate state

GET should be safe. This rule uses declared names and summaries only, so it identifies candidates for review rather than proving runtime mutation.
