---
id: CASE004
category: Naming
matcher: naming
scope: header
parameters: { convention: hyphenated, match: non-conforming }
---

# CASE004 — Header does not use conventional hyphenated naming

## Intent

Custom HTTP headers should use conventional hyphen-separated naming. This
keeps field names readable and compatible with normal HTTP tooling.

## Detection and scope

The rule has `header` scope and uses the `naming` rule:

```yaml
parameters: { convention: hyphenated, match: non-conforming }
```

It examines declared operation header parameters. A name conforms when it
matches `[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+`, meaning at least two alphanumeric
groups separated by a hyphen. Non-conforming names are reported at the header
parameter pointer with `Name does not use the configured convention`.

## Review-candidate example

```yaml
parameters:
  - in: header
    name: request_id
    schema: { type: string }
```

`request-id` follows the configured convention.

## Compliant example

```yaml
parameters:
  - in: header
    name: Request-ID
    schema: { type: string }
```

## Parameters, references, and limitations

Both parameters select the fixed hyphenated grammar. Matching is ASCII and
allows uppercase letters and digits; this rule does not enforce lowercase or
validate reserved-header rules. It does not inspect response headers, runtime
traffic, descriptions, or headers supplied by gateways. Referenced parameters
count only after host normalisation.
