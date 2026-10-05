---
id: JSON006
category: JSON
matcher: schema
scope: property
parameters: { required: false, nullable: true, semantics: undefined }
---

# JSON006 — Optional property explicitly models null without defined semantics

## Intent

An optional property’s absence and an explicit `null` should not be separate
states without meaningful semantics. If clients must distinguish “not
provided” from “provided as null,” the contract should explain that distinction
and its update behavior. OpenAPI can identify the optional and nullable
declaration; it cannot prove the semantics are undefined.

## Detection and scope

The rule has `property` scope and uses the `schema` rule:

```yaml
parameters: { required: false, nullable: true, semantics: undefined }
```

The rule reports a property when it is not required and is explicitly
nullable. `semantics: undefined` records the policy concern but does not add a
machine-checkable condition. Findings point to the property with
`Optional property explicitly permits null`.

## Review-candidate example

This property is optional and explicitly permits null, so it is reported:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        nickname:
          type: string
          nullable: true
```

The contract should explain whether omission means “leave unchanged,” “use a
default,” or something distinct from an explicit JSON `null`:

```json
{ "nickname": null }
```

## Compliant examples

A required nullable property is excluded by this rule:

```yaml
properties:
  nickname:
    type: string
    nullable: true
required: [nickname]
```

An optional non-nullable property is also excluded:

```yaml
properties:
  nickname: { type: string }
```

## Parameters, references, and limitations

The rule requires `required: false` and `nullable: true`; `semantics:
undefined` is metadata for a concern that the rule cannot establish.
OpenAPI 3.1 union forms such as `type: [string, 'null']` are considered only
if the host normalises them into its nullable fact. The rule does not
inspect descriptions, examples, JSON payloads, PATCH behavior, defaults,
serialization, or runtime semantics. Referenced properties count only after
host normalisation. Findings require human review of the intended meaning of
absence and null.
