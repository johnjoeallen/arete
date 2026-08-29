---
id: compatibility
language: starlark
source: Detector.star
scopes: [api, operation, path, schema-property]
parameters:
  change:
    type: enum
    required: true
    values: [interface-removed, property-removed, property-renamed, operation-removed, enum-value-removed, http-binding-changed, type-changed, resource-name-format-changed, url-format-changed, required-request-property-added, incompatible-response-property-added, response-enum-value-added]
---

# Compatibility detector

Compatibility checks require a baseline API specification. Until comparison
input is supplied, this detector deliberately returns no automated evidence;
it never guesses that a current contract changed. The rule remains available
for a future comparison-mode execution.
