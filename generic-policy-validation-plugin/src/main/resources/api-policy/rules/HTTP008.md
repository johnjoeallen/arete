---
id: HTTP008
category: HTTP
detector: operation-semantics
scope: path
parameters: { match: unsupported-operation-semantics-unclear }
---

# HTTP008 — Supported operation semantics are unclear

The initial detector safely returns no match for standard OpenAPI methods. Detecting unsupported methods requires a later stable-model extension.
