---
id: VERSION004
category: Versioning
matcher: versioning
scope: api
parameters: { match: absent }
---

# VERSION004 — Interface is unversioned

## Intent

This rule identifies APIs whose OpenAPI description does not show an explicit
interface-versioning mechanism. Versioning can make incompatible evolution
visible to clients and can give clients a stable contract to select. Other
organisations deliberately evolve a single interface using compatibility
rules, so this finding is a policy choice rather than an assertion that the
API is invalid.

## Detection

The rule has API scope and uses the `versioning` rule with
`parameters: { match: absent }`. It reports one finding at `/paths` when no
path in the API exposes a recognised version in any of these locations:

* a path segment matching `v` followed by one or more digits, or `version`
  followed by one or more digits (for example, `/v1/customers` or
  `/version2/customers`);
* an operation header parameter named `version`, `api-version`, `api_version`,
  or `x-api-version` (the name match is case-insensitive); or
* an operation request or response media type containing a version-like
  `v`-number or `version`-number sequence (for example,
  `application/vnd.example.v2+json`).

The rule stops after finding the first recognised location. Therefore an
API with even one versioned path, header, or media type is considered
versioned for this rule; the rule does not require every operation to use the
same mechanism.

## Violating example

This specification has no recognised URI, header, or media-type version:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /customers:
    get:
      responses:
        '200':
          description: OK
```

It produces a VERSION004 finding at `/paths`. The `info.version` value is the
version of this document, not one of the version locations inspected by this
rule, so it does not prevent the finding.

## Compliant examples

A version-like URI segment is sufficient:

```yaml
paths:
  /v1/customers:
    get: {}
```

A version header is also sufficient:

```yaml
paths:
  /customers:
    get:
      parameters:
        - in: header
          name: X-API-Version
          schema: { type: string }
      responses: {}
```

Likewise, a versioned media type is recognised in the operation’s media-type
facts:

```yaml
paths:
  /customers:
    get:
      responses:
        '200':
          description: OK
          content:
            application/vnd.example.v2+json:
              schema: { type: object }
```

The examples are compliant with VERSION004 because each exposes at least one
recognised version location. They may still be reported by VERSION001,
VERSION002, or VERSION003 when those rules are enabled and configured to
match a present version.

## Parameters, references, and missing information

VERSION004 accepts only `match: absent`; `location` is not used for this
API-level check. The rule reads the host’s normalised path, operation
parameter, and media-type facts, so `$ref` values are handled only to the
extent that the host resolves them into those facts. An unresolved reference,
an omitted operation detail, or an incomplete specification can therefore
make a real versioning mechanism invisible and produce a false positive.

The rule does not inspect `info.version`, server variables or URLs, query
parameters, cookies, arbitrary extension fields, payload fields, response
headers, or version values transmitted in example messages. It also does not
validate semantic-version syntax, compare versions, or require consistent
versioning across operations. Regex-based recognition is intentionally
heuristic: unusual but valid conventions are candidates for review rather
than proof that an API is unversioned.
