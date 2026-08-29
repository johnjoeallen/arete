---
id: operation-semantics
language: starlark
source: Detector.star
scopes:
  - operation
  - path
parameters:
  method:
    type: enum
    required: false
    values: [GET, POST, PUT, PATCH, DELETE]
  expected:
    type: enum
    required: false
    values: [safe]
  match:
    type: enum
    required: false
    values: [full-resource-replacement, partial-update, inconsistent-method-resource-semantics, unsupported-operation-semantics-unclear]
---

# Operation semantics detector

This detector applies transparent, documentation-and-path-name heuristics to
identify likely HTTP semantic mismatches. OpenAPI describes an interface, not
actual server behaviour, so these results are indicators for review rather
than proof that an operation mutates state or replaces a whole resource.

It intentionally exposes a small fixed vocabulary. A future detector may add
more reliable evidence through explicit vendor extensions or runtime tests.
