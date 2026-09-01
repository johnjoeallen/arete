---
id: STANDARD005
category: Standards
matcher: path-count
scope: api
parameters: { maximum: 8, maximum-depth: 2 }
---

# STANDARD005 — Resource path is too deeply nested

## Intent

Resource paths should not become so deeply nested that clients must understand
an overly long ownership chain to address a resource. Limiting nesting can
improve discoverability and reduce coupling between otherwise independent
resources. The configured depth is a convention and may need exceptions for
genuine containment relationships.

## Detection and scope

The rule has `api` scope and uses the `path-count` rule:

```yaml
parameters: { maximum: 8, maximum-depth: 2 }
```

When `maximum-depth` is present, the rule counts non-parameter path
segments (segments beginning with `{` are ignored). It reports each path whose
count is greater than 2 nested resource levels with `Resource path exceeds the
maximum nesting depth`.
The `maximum: 8` value is not used by this rule branch.

## Review-candidate example

This path has three resource segments—`customers`, `orders`, and `items`—and
is reported:

```yaml
paths:
  /customers/{customer_id}/orders/{order_id}/items:
    get: { responses: { '200': { description: Order items } } }
```

## Compliant example

This path has two non-parameter resource segments and does not match:

```yaml
paths:
  /customers/{customer_id}/orders:
    get: { responses: { '200': { description: Customer orders } } }
```

## Parameters, references, and limitations

`maximum-depth: 2` controls this rule; the rule also supports a separate
top-level resource-count mode through `maximum`, but STANDARD005 does not use
that mode. The check is structural: it does not inspect operation methods,
resource schemas, summaries, references, or whether a deeper path is useful or
actually deployed. Literal path segments that are not resources are counted,
while all parameter segments are excluded. Findings should be reviewed in
the context of the domain model.
