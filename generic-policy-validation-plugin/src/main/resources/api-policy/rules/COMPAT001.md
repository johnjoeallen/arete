---
id: COMPAT001
category: Compatibility
detector: compatibility
scope: api
parameters: { change: interface-removed }
---

# COMPAT001 — Existing service or interface is removed

Removing an existing externally available interface can break consumers. This rule requires a baseline specification and is not evaluated without comparison input.

## Detection and scope

The rule has `api` scope and uses the `compatibility` detector:

```yaml
parameters: { change: interface-removed }
```

This category is intended to compare the current API with a baseline and
identify removal of the interface itself. The current detector has no baseline
input and deliberately returns no automated occurrences, so COMPAT001 is not
evaluated from a current document alone.

## Review guidance

Before retiring an externally available API, compare its published baseline
with the proposed state, identify active consumers, announce deprecation, and
provide a replacement or migration timeline. Removing all paths or withdrawing
an API from a catalogue may each require separate operational review.

## Configuration and limitations

`change: interface-removed` selects the future comparison category. No
baseline, catalogue state, client inventory, deployment state, or runtime
traffic is currently supplied. References and missing comparison input produce
no evidence rather than a guessed violation.
