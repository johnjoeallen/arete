---
id: REST006
category: Resource design
matcher: naming
scope: schema
parameters: { suffix: Response, match: present }
---

# REST006 — Schema name ends in Response

## Intent

Schema names should describe the represented data rather than response
direction. Suffixes such as `CustomerResponse` can be useful when the response
really is a distinct envelope, but they often duplicate context already
provided by the operation and make reusable models harder to discover.

## Detection and scope

The rule has `schema` scope and uses the `naming` rule:

```yaml
parameters: { suffix: Response, match: present }
```

The rule examines each normalised component schema name. It reports names
that end exactly in the case-sensitive suffix `Response`, with an diagnostic
at the schema pointer and the message `Name has prohibited suffix Response`.
No operation, path, schema properties, or schema content is inspected.

## Review-candidate example

This component schema is reported:

```yaml
components:
  schemas:
    CustomerResponse:
      type: object
      properties:
        id: { type: string }
```

If it represents an ordinary customer, `Customer` may be a clearer reusable
name. If it is a genuinely distinct envelope, the team can document the
reason for the exception.

## Compliant example

This schema name does not end in `Response` and does not match:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        id: { type: string }
```

`customerResponse` and `Customerresponse` are also not exact matches because
suffix matching is case-sensitive.

## Parameters, references, and limitations

The rule fixes `suffix: Response` and `match: present`; the naming rule’s
other conventions and semantic modes are not used. It does not infer whether
a schema is used for requests or responses, inspect `$ref` usage, evaluate
properties, or judge whether the suffix is semantically justified. Unusual
schema registries or unresolved components may not appear in the host’s
normalised `schemas` collection. Findings are naming-policy candidates for
review rather than automatic renaming instructions.
