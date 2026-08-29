---
id: REST005
category: Resource design
detector: naming
scope: schema
parameters: { suffix: Request, match: present }
---

# REST005 — Schema name ends in Request

## Intent

Schema names should describe the represented business concept rather than the
request direction. A suffix such as `CustomerRequest` can be useful when the
request is genuinely a distinct model, but it often duplicates context already
provided by the operation and makes reusable schemas harder to discover.

## Detection and scope

The rule has `schema` scope and uses the `naming` detector:

```yaml
parameters: { suffix: Request, match: present }
```

The detector examines each normalised component schema name. It reports names
that end exactly in the case-sensitive suffix `Request`, at the schema pointer,
with the message `Name has prohibited suffix Request`. No operation, path,
schema properties, or request/response usage is inspected.

## Review-candidate example

This schema is reported:

```yaml
components:
  schemas:
    CustomerRequest:
      type: object
      properties:
        name: { type: string }
```

If it represents an ordinary customer, `Customer` may be a clearer reusable
name. Retain the suffix when the request model is intentionally distinct and
document that reason.

## Compliant example

This schema name does not end in `Request` and does not match:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        name: { type: string }
```

`customerRequest` and `Customerrequest` are also not exact matches because the
configured suffix comparison is case-sensitive.

## Parameters, references, and limitations

The rule fixes `suffix: Request` and `match: present`; other naming
conventions, semantic modes, and suffixes supported by the detector are not
used. It does not infer whether a schema is used for a request, inspect `$ref`
usage or properties, or judge whether the suffix is semantically justified.
Unresolved or external schemas may not appear in the host’s normalised schema
collection. Findings are naming-policy candidates, not automatic rename
instructions.
