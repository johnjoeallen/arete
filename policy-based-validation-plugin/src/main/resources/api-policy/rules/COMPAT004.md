---
id: COMPAT004
category: Compatibility
matcher: compatibility
scope: operation
parameters: { change: operation-removed }
---

# COMPAT004 — Existing operation is removed

## Intent

Removing an existing operation can break consumers that use it. Requires a baseline specification.

## Detection and scope

The rule has `operation` scope and uses the `compatibility` rule with:

```yaml
parameters: { change: operation-removed }
```

This change type is intended to compare operations in a current specification
with a baseline. The current rule receives no baseline and deliberately
returns no automated diagnostics, so running COMPAT004 against one document
does not report a finding.

## Review guidance

Compare the baseline operation:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

with the proposed contract. Removing the GET can break generated clients,
bookmarks, and integrations; assess deprecation and migration before removal.

## Unchanged comparison example

With no baseline change supplied, the current document is not reported. A
future comparison retaining the same operation should produce no finding.

## Configuration and limitations

`change: operation-removed` selects the future comparison category. No baseline
document, operation diff, client inventory, runtime traffic, or deprecation
history is currently supplied. References and missing comparison input produce
no evidence rather than an inferred diagnostic.
