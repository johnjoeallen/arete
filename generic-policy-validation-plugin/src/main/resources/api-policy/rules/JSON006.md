---
id: JSON006
category: JSON
detector: schema
scope: property
parameters: { required: false, nullable: true, semantics: undefined }
---

# JSON006 — Optional property explicitly models null without defined semantics

An optional property's absence and an explicit `null` should not be separate states without meaningful semantics. OpenAPI can identify the optional and nullable declaration; it cannot prove the semantics are undefined.
