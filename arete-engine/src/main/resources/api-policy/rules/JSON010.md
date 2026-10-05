---
id: JSON010
category: JSON
matcher: common-field
scope: property
parameters: { convention: default }
---

# JSON010 — Common field has an inconsistent type

## Intent

Common fields should use predictable representations across schemas. This rule
expects `id` to be a string and `created` or `modified` to be date-time
strings, reducing client special cases and improving reuse.

## Detection and scope

The rule has `property` scope and uses the `common-field` rule:

```yaml
parameters: { convention: default }
```

The rule checks properties with the exact, case-sensitive names `id`,
`created`, and `modified`. It reports `id` unless its type is `string`; it
reports `created` and `modified` unless both type `string` and format
`date-time` are present. Findings point to the property and say `Common field
has an inconsistent type or format`.

## Review-candidate example

Both properties are reported:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        id: { type: integer }
        created: { type: string }
```

## Compliant example

These declarations satisfy the configured convention:

```yaml
properties:
  id: { type: string }
  created: { type: string, format: date-time }
  modified: { type: string, format: date-time }
```

## Parameters, references, and limitations

`convention: default` selects the fixed convention; it does not configure
different names or types. Names such as `ID`, `createdAt`, and `modified_at`
are not checked by this rule. It does not inspect examples, serialization,
runtime values, schema inheritance, or business-specific identifier formats.
Referenced schemas count only when their properties are resolved into the
normalised model. Findings are policy candidates, not universal type rules.
