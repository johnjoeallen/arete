---
id: STANDARD006
category: Standards
detector: path-count
scope: api
parameters: { maximum: 8, nested-root: true }
---

# STANDARD006 — Nested resource should be exposed at the root

## Intent

Nested resource types should also be exposed through a root resource path where
appropriate. A root path can make an independently addressable resource easier
to discover and avoid forcing clients through an unrelated parent resource.
This is a convention, not a universal requirement.

## Detection and scope

The rule has `api` scope and uses the `path-count` detector:

```yaml
parameters: { maximum: 8, nested-root: true }
```

With `nested-root: true`, the detector collects the first non-parameter path
segment from every path as a root set. It reports a path when it has more than
one non-parameter segment and its final non-parameter segment is not in that
root set. The finding points to the path and says `Nested resource type is not
exposed as a root resource`.

## Review-candidate example

Because `orders` appears only below `customers`, this path is reported:

```yaml
paths:
  /customers/{customer_id}/orders:
    get: { responses: { '200': { description: Orders } } }
```

Adding a root path makes the type independently discoverable:

```yaml
paths:
  /orders:
    get: { responses: { '200': { description: Order collection } } }
```

## Compliant example

Here the nested `orders` type also has a root path, so neither path is flagged
by this rule:

```yaml
paths:
  /orders: { get: { responses: { '200': { description: Orders } } } }
  /customers/{customer_id}/orders: { get: { responses: { '200': { description: Orders } } } }
```

## Parameters, references, and limitations

The rule’s `maximum: 8` parameter is retained in its metadata but is not used
when `nested-root: true` selects this detector branch. Path segments beginning
with `{` are ignored, and the detector compares literal segment names without
domain or schema knowledge. It does not judge whether a nested resource
should be independently addressable, inspect operations or references, or
consider runtime routes. Missing or unusual path representations can affect
the normalised facts; findings are review candidates.
