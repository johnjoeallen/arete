---
id: COMPAT004
category: Compatibility
detector: compatibility
scope: operation
parameters: { change: operation-removed }
---

# COMPAT004 — Existing operation is removed

Removing an existing operation can break consumers that use it. Requires a baseline specification.

## Detection and scope

The rule has `operation` scope and uses the `compatibility` detector with:

```yaml
parameters: { change: operation-removed }
```

This change type is intended to compare operations in a current specification
with a baseline. The current detector receives no baseline and deliberately
returns no automated occurrences, so running COMPAT004 against one document
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

## Configuration and limitations

`change: operation-removed` selects the future comparison category. No baseline
document, operation diff, client inventory, runtime traffic, or deprecation
history is currently supplied. References and missing comparison input produce
no evidence rather than an inferred violation.
