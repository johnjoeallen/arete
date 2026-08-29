---
id: REST002
category: Resource design
detector: naming
scope: path-segment
parameters: { semantic: collection }
---

# REST002 — Collection resource uses a singular noun

## Intent

A path representing a collection should normally use a plural noun so that
clients can distinguish a collection from one member. This convention improves
readability but does not handle every language, irregular plural, or domain
term, so findings are candidates for review.

## Detection and scope

The rule has `path-segment` scope and uses the `naming` detector:

```yaml
parameters: { semantic: collection }
```

The detector examines every normalised path segment. A segment is considered
plural when its lowercase spelling ends in `s` and has more than one
character. With `semantic: collection`, it reports segments that fail that
test, at the segment pointer, with `Collection name is singular`.

## Review-candidate example

The `customer` segment is reported by the heuristic:

```yaml
paths:
  /customer:
    get: { responses: { '200': { description: Customer collection } } }
```

If the endpoint represents many customers, `/customers` may be clearer.

## Compliant example

The segment `customers` ends in `s` and does not match:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

## Parameters, references, and limitations

The rule configures only `semantic: collection`; it does not use the naming
detector’s conventions, suffixes, or type filters. It does not understand
irregular plurals such as `people`, uncountable nouns, locale, path parameter
values, schemas, methods, or runtime semantics. Parameters are exposed as
separate segments by the host and should be reviewed in context. Referenced
path items count only when normalised by the host.
