---
id: JSON011
category: JSON
matcher: date-time-name
scope: property
parameters: { suffix: _at }
---

# JSON011 — Date-time property name lacks the configured suffix

## Intent

Date-time property names should make their temporal meaning visible. A
consistent suffix such as `_at` helps clients distinguish timestamps from
ordinary strings and improves generated documentation.

## Detection and scope

The rule has `property` scope and uses the `date-time-name` rule:

```yaml
parameters: { suffix: _at }
```

It examines schema properties whose type is exactly `string` and whose format
is exactly `date-time`. If the property name does not end in `_at`, the
property is reported at its pointer with `Date-time property name does not end
with _at`.

## Review-candidate example

The `created` property is reported:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        created:
          type: string
          format: date-time
```

`created_at` communicates the convention more explicitly.

## Compliant example

This property ends in the configured suffix:

```yaml
properties:
  created_at:
    type: string
    format: date-time
```

Properties that are not declared as `string` with `date-time` are outside the
rule, even if their names do not end in `_at`.

## Parameters, references, and limitations

`suffix` is required and is fixed to `_at` by this rule; matching is
case-sensitive. The rule does not infer temporal meaning from names,
examples, descriptions, JSON values, or runtime payloads, and it does not
validate timestamp syntax or timezone handling. Referenced schemas count only
when the host resolves their properties into its normalised collection.
Findings are naming-convention candidates.
