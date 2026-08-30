---
id: STANDARD001
category: Standards
matcher: hostname
scope: api
parameters: { convention: lowercase-hyphenated }
---

# STANDARD001 — Server hostname is not functionally named

## Intent

Declared server hostnames should use lowercase, hyphen-separated functional
names. Names such as `api.example.com` or `customer-api.example.com` are easier
to read and distinguish than hostnames that depend on opaque casing or
underscores.

## Detection and scope

The rule has `api` scope and uses the `hostname` rule:

```yaml
parameters: { convention: lowercase-hyphenated }
```

For each declared OpenAPI server URL, the rule extracts its host. A host
is accepted when it matches the entire case-sensitive pattern
`[a-z0-9]+(?:-[a-z0-9]+)*`, meaning lowercase letters/digits in labels joined
by hyphens. Hosts that are absent or fail this pattern produce an diagnostic at
`/servers` with `Server hostname is not lowercase hyphenated`.

## Review-candidate example

These server URLs contain uppercase or underscore characters in the host:

```yaml
servers:
  - url: https://Customer_API.example.com/v1
  - url: https://api.example.com/v1
```

The first host is reported; the second host is accepted by this rule.

## Compliant example

```yaml
servers:
  - url: https://customer-api.example.com/v1
```

The path, scheme, port, and URL variables do not affect hostname convention
matching.

## Parameters, references, and limitations

`convention: lowercase-hyphenated` is the only configured convention. The
rule checks only declared server URLs and does not inspect DNS, certificates,
runtime requests, documentation prose, or hosts supplied by a gateway. It
does not validate that a hostname is resolvable or functionally meaningful,
only its extracted host spelling. Invalid or unresolved server URLs may have
no extractable host and are reported as candidates for review.
