---
id: bulk-operation
language: starlark
source: Detector.star
scopes: [operation]
parameters:
  operation-type:
    type: enum
    required: false
    values: [create]
  expected-method:
    type: enum
    required: false
    values: [POST, PUT, DELETE]
  payload:
    type: enum
    required: false
    values: [collection]
  method:
    type: enum
    required: false
    values: [PUT, DELETE]
  target-selection:
    type: enum
    required: false
    values: [search-criteria]
---

# Bulk-operation detector

Uses conservative path, method, and request-body facts to identify likely bulk
operations. It cannot prove runtime cardinality or business intent. A create
rule flags a POST requirement when a create-like operation is not a collection
POST; mutation rules flag PUT/DELETE paths that contain query-style selection
terms.
