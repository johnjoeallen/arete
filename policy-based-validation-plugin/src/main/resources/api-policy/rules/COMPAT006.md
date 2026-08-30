---
id: COMPAT006
category: Compatibility
matcher: compatibility
scope: operation
parameters: { change: http-binding-changed }
---

# COMPAT006 — HTTP binding is changed

## Intent

Changing an operation's HTTP method, path or other binding can break existing consumers. Requires a baseline specification.

## Detection and scope

The rule has `operation` scope and uses the `compatibility` rule with:

```yaml
parameters: { change: http-binding-changed }
```

This change type is intended to compare an operation’s method, path, and other
HTTP binding with a baseline specification. The current rule has no
baseline input and deliberately returns no automated diagnostics. As a result,
COMPAT006 is currently a comparison-mode capability and does not report a
finding when run against only the current document.

## Review guidance

Compare a baseline such as:

```yaml
# baseline
paths:
  /customers/{customer_id}:
    get: { responses: { '200': { description: Customer } } }
```

with a proposed contract that changes the method or path, for example
`/customers/{id}` or POST. Determine whether existing clients can continue to
call the old binding or whether a compatibility version and migration are
needed.

## Unchanged comparison example

With no baseline change supplied, the current document is not reported. A
future comparison retaining the same method and path should produce no finding.

## Configuration and limitations

`change: http-binding-changed` selects the future comparison category. No
baseline parameter, runtime traffic, server routing, aliases, redirects, or
client inventory is currently inspected. References and missing comparison
input produce no evidence rather than an inferred diagnostic. Findings from a
future comparison should be treated as compatibility evidence requiring review.
