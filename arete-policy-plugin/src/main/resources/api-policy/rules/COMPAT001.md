---
id: COMPAT001
category: Compatibility
matcher: compatibility
scope: api
parameters: { change: interface-removed }
---

# COMPAT001 — Existing service or interface is removed

## Intent

Removing an existing externally available interface can break consumers. This rule requires a baseline specification and is not evaluated without comparison input.

## Detection and scope

The rule has `api` scope and uses the `compatibility` rule:

```yaml
parameters: { change: interface-removed }
```

This category is intended to compare the current API with a baseline and
identify removal of the interface itself. The current rule has no baseline
input and deliberately returns no automated diagnostics, so COMPAT001 is not
evaluated from a current document alone.

## Review guidance

Before retiring an externally available API, compare its published baseline
with the proposed state, identify active consumers, announce deprecation, and
provide a replacement or migration timeline. Removing all paths or withdrawing
an API from a catalogue may each require separate operational review.

## Future comparison shape

The intended comparison is represented by a baseline and a proposed document,
not by either document alone:

```yaml
# baseline
openapi: 3.0.3
info: { title: Orders API, version: 1.0.0 }
paths:
  /orders: { get: { responses: { '200': { description: OK } } } }
```

```yaml
# proposed
openapi: 3.0.3
info: { title: Orders API, version: 2.0.0 }
paths: {}
```

Once baseline comparison is implemented, this pair would be a review candidate.

## Unchanged comparison example

With no baseline change supplied, the current document is not reported. A
future comparison of the same interface against itself should likewise produce
no finding.

## Configuration and limitations

`change: interface-removed` selects the future comparison category. No
baseline, catalogue state, client inventory, deployment state, or runtime
traffic is currently supplied. References and missing comparison input produce
no evidence rather than a guessed diagnostic.
