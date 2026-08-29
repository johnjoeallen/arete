---
id: STANDARD009
category: Standards
detector: query-collection
scope: query-parameter
parameters: { style: form, explode: true }
---

# STANDARD009 — Collection query parameter uses the wrong serialization

Collection-valued query parameters should use the serialization configured by
the active policy. The default policy requires OpenAPI `form` style with
`explode: true`, which serializes `tags: [red, blue]` as
`?tags=red&tags=blue`.

The detector checks array-valued query parameters and applies OpenAPI defaults
when `style` or `explode` is omitted. It does not inspect runtime URLs.

## Violation

```yaml
parameters:
  - in: query
    name: tags
    style: pipeDelimited
    explode: false
    schema:
      type: array
      items: { type: string }
```

## Compliant

```yaml
parameters:
  - in: query
    name: tags
    style: form
    explode: true
    schema:
      type: array
      items: { type: string }
```

The expected style and explode value are policy parameters and may be
overridden per policy.
