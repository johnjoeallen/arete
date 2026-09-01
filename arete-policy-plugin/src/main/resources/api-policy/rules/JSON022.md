---
id: JSON022
category: JSON
matcher: schema
scope: property
parameters: { max-length: absent }
---

# JSON022 — String property has no maximum length

## Intent

Every `string` property should declare a `maxLength`. A stated upper bound
documents the field, lets clients size inputs and storage, and prevents an
unbounded string from being used to exhaust memory or downstream limits.

## Detection and scope

The rule has `property` scope and uses the `schema` matcher with
`max-length: absent`. Every component-schema property whose `type` is
`string` and that declares no `maxLength` is reported once at its property
pointer.

## Review-candidate example

`comment` is reported:

```yaml
components:
  schemas:
    Review:
      type: object
      properties:
        comment: { type: string }
```

## Compliant example

```yaml
components:
  schemas:
    Review:
      type: object
      properties:
        comment: { type: string, maxLength: 2000 }
```

## Parameters, references, and limitations

`max-length: absent` is the rule's only mode. It does not inspect `format`,
`pattern`, or `enum` — a property constrained to an enum still counts as
unbounded here. Non-string properties are ignored.
