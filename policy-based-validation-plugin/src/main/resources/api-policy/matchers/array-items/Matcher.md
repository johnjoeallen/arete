---
id: array-items
language: distill
source: Matcher.dsl
scopes: [api]
parameters: {}
---

# Array-items rule

Reports an array-typed schema that declares no `items`. A component schema
whose `type` is `array`, and any object property whose `type` is `array`, must
declare `items`; without it the element type is undefined and generators,
validators, and documentation tools cannot describe the array's contents.

The rule emits one occurrence per offending schema or property, at that
schema's or property's pointer.
