---
id: STANDARD009
category: Standards
matcher: query-collection
scope: query-parameter
parameters: { style: form, explode: true }
---

# STANDARD009 — Collection query parameter uses the wrong serialization

Collection-valued query parameters should use the serialization configured by
the active policy. The default policy requires OpenAPI `form` style with
`explode: true`, which serializes `tags: [red, blue]` as
`?tags=red&tags=blue`.

The rule checks array-valued query parameters and applies OpenAPI defaults
when `style` or `explode` is omitted. It does not inspect runtime URLs.

## Diagnostic

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

## Detection and scope

The rule has `query-parameter` scope and uses the `query-collection` rule.
It examines only query parameters whose normalised schema type is `array`.
When `style` is omitted, the rule applies the OpenAPI default `form`; when
`explode` is omitted, it defaults to true for form style and false otherwise.
An array parameter is reported when either effective value differs from the
configured policy value.

## Configuration and limitations

The default policy requires `style: form` and `explode: true`, but both values
may be overridden per policy. The rule does not inspect scalar parameters,
actual URL encoding, delimiters in runtime requests, request bodies, or server
parsers. Referenced parameters count only after host normalisation. The rule
checks serialization declarations, not whether a client actually sends the
declared representation.
