---
id: header-schema
language: starlark
source: Detector.star
scopes: [response]
parameters: {}
---

# Header-schema detector

Reports a documented response header that declares neither a `schema` nor a
`content` object, so its type is undefined.
