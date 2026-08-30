---
id: VERSION003
category: Versioning
matcher: versioning
scope: media-type
parameters: { location: media-type, match: present }
---

# VERSION003 — Version appears in the media type

## Intent

This rule identifies an API that selects an interface version through an HTTP
media type. Media-type versioning can allow several representations or
contract revisions to coexist, but it also makes client negotiation and
compatibility management more complex. Whether that is appropriate is a
policy choice; a finding is a candidate for review, not proof that the API is
incorrect.

## Detection

The rule has `media-type` scope and uses the `versioning` rule with:

```yaml
parameters: { location: media-type, match: present }
```

For each path, the rule examines the host’s normalised `mediaTypes` list
for every operation. That list includes media types declared by both request
bodies and responses. A path is reported when at least one media type matches
either of these case-insensitive patterns:

* any string containing `v` followed by one or more digits, optionally after a
  `+`; or
* any string containing `version` followed by one or more digits.

The finding points to the path and has the message `Interface version is
exposed through media-type`. The rule does not report the individual
content-type location or distinguish request from response in the finding.

## Review-candidate example

This operation advertises a version in its response media type and therefore
produces a VERSION003 finding for `/customers`:

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
          content:
            application/vnd.example.v2+json:
              schema: { type: object }
```

The same applies to a request media type:

```yaml
paths:
  /customers:
    post:
      requestBody:
        content:
          application/vnd.example+version2:
            schema: { type: object }
      responses:
        '204': { description: Updated }
```

A path such as `/v1/customers` does not itself make VERSION003 match, but it
can be reported separately by VERSION001. If the path also uses
`application/vnd.example.v2+json`, both rules can produce findings.

## Compliant example

This operation uses ordinary media types with no version-like sequence, so it
does not produce a VERSION003 finding:

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
          content:
            application/json:
              schema: { type: object }
```

The corresponding wire representation is likewise not versioned according to
this rule:

```http
GET /customers HTTP/1.1
Accept: application/json

HTTP/1.1 200 OK
Content-Type: application/json
```

## Parameters, references, and limitations

`location` must be `media-type` for this rule. The rule’s `match: present`
branch uses the configured location and reports every matching path; it does
not perform an API-wide absence check. `match: absent` is supported by the
rule for VERSION004, not by this rule’s metadata.

The rule uses normalised operation facts, so referenced request bodies,
responses, and content maps are considered only if the host resolves them
into `mediaTypes`. An unresolved or incomplete `$ref` can hide a media type
and cause a false negative. Media types without a version-like `v` or
`version` followed by digits are not recognised, even if an organisation uses
another convention. Conversely, the broad substring match can recognise a
string that contains those characters incidentally.

VERSION003 does not inspect `info.version`, server URLs, path segments, header
parameters, query parameters, payload fields, examples, or the actual
`Content-Type` header exchanged at runtime. It does not validate media-type
syntax, semantic-version rules, negotiation behavior, or consistency between
request and response versions. These limitations make the result contract
evidence for human review rather than a complete judgement about versioning.
