---
id: HTTP002
category: HTTP
detector: operation-semantics
scope: operation
parameters: { method: POST, match: full-resource-replacement }
---

# HTTP002 — POST is used for complete resource replacement

PUT is normally more appropriate when replacing the complete representation of an identified resource.
