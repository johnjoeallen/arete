---
id: STANDARD004
category: Standards
matcher: path-count
scope: api
parameters: { maximum: 8 }
---

# STANDARD004 — API has too many top-level resource types

## Intent

An API should remain focused and expose no more than eight top-level resource
types. A large catalogue can make discovery, ownership, and governance harder;
the limit is a convention and may not suit a deliberately broad platform API.

## Detection and scope

The rule has `api` scope and uses the `path-count` rule:

```yaml
parameters: { maximum: 8 }
```

The rule takes the first non-parameter segment of every declared path,
deduplicates those segments, and compares the count with eight. If the count
is greater than eight, it reports one API-level diagnostic at `/paths`, with a
message containing the count and configured maximum. Segments beginning with
`{` are ignored.

## Review-candidate example

An API with paths rooted at nine distinct resources—such as `/customers`,
`/orders`, `/invoices`, `/payments`, `/products`, `/suppliers`, `/shipments`,
`/reports`, and `/audit-events`—is reported. The review question is whether
these resources belong in one coherent API or should be split into bounded
services or separately documented areas.

## Compliant example

An API whose paths use at most eight distinct first resource segments does not
match, regardless of how many operations those resources contain:

```yaml
paths:
  /customers: { get: { responses: { '200': { description: OK } } } }
  /orders: { get: { responses: { '200': { description: OK } } } }
```

## Parameters, references, and limitations

`maximum: 8` is the threshold for this rule. The rule also supports
maximum-depth and nested-root modes, but those are separate branches and are
not configured here. It counts literal normalised path segments rather than
schemas, tags, ownership metadata, or runtime routes. Referenced path items
count only if resolved by the host. Version prefixes or technical paths may
be counted as resources, and the rule cannot judge whether a broad API is
actually over-scoped; findings are review candidates.
