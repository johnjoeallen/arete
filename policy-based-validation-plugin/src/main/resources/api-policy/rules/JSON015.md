---
id: JSON015
category: JSON
matcher: schema
scope: property
parameters: { enum-case: upper-snake-case }
---

# JSON015 — Enum values are not UPPER_SNAKE_CASE

## Intent

String enum values should use `UPPER_SNAKE_CASE` so that symbolic constants
have a consistent, easy-to-scan representation across JSON payloads and
clients. This is a naming convention; some APIs may intentionally preserve
external or human-readable values.

## Detection and scope

The rule has `property` scope and uses the `schema` rule:

```yaml
parameters: { enum-case: upper-snake-case }
```

For each schema property with an enum, the rule checks every string enum
value against the case-sensitive pattern:

```text
[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*
```

If any string value fails, the property is reported at its property pointer
with `Property matches the configured schema rule`. Non-string enum values do
not participate in this check.

## Review-candidate example

The `state` property is reported because `in progress` and `completed` are not
UPPER_SNAKE_CASE:

```yaml
components:
  schemas:
    Job:
      type: object
      properties:
        state:
          type: string
          enum: [in progress, completed, FAILED]
```

Values such as `IN_PROGRESS`, `COMPLETED`, and `FAILED` satisfy the configured
pattern.

## Compliant example

Every string value in this enum matches:

```yaml
components:
  schemas:
    Job:
      type: object
      properties:
        state:
          type: string
          enum: [QUEUED, IN_PROGRESS, FAILED]
```

## Parameters, references, and limitations

`enum-case: upper-snake-case` is the rule’s only configured mode. It does not
require the property itself to be declared as `type: string`; non-string enum
values are simply ignored, and a mixed enum is reported only when a string
value fails. The rule does not inspect runtime values, descriptions,
examples, payloads, localization, or whether a value is externally mandated.
Referenced schemas count only when the host resolves their properties into the
normalised model. Findings are convention candidates for review.
