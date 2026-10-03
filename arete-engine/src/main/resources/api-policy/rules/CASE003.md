---
id: CASE003
category: Naming
matcher: naming
scope: query-parameter
parameters: { convention: snake_case, match: non-conforming }
---

# CASE003 — Query parameter is not snake_case

## Intent

Query parameter names should follow a predictable snake_case convention so
clients can construct URLs consistently.

## Detection and scope

The rule has `query-parameter` scope and uses the `naming` rule:

```yaml
parameters: { convention: snake_case, match: non-conforming }
```

It examines declared operation parameters whose `in` value is `query`. A name
conforms when it matches `[a-z][a-z0-9]*(?:_[a-z0-9]+)*`: lowercase initial
letter, lowercase letters or digits, and optional underscore-separated groups.
Non-conforming names are reported at the parameter pointer with `Name does not
use the configured convention`.

## Review-candidate example

```yaml
parameters:
  - in: query
    name: pageSize
    schema: { type: integer }
```

`page_size` follows the configured convention.

## Compliant example

```yaml
parameters:
  - in: query
    name: page_size
    schema: { type: integer }
```

## Parameters, references, and limitations

The rule fixes snake_case and does not inspect path parameters, headers,
properties, parameter values, styles, descriptions, or runtime URLs.
Uppercase, hyphens, leading digits, and repeated/leading underscores do not
conform. Referenced parameters count only after host normalisation; naming
exceptions for external standards may be appropriate.
