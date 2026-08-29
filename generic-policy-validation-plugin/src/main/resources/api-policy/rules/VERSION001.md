---
id: VERSION001
category: Versioning
detector: versioning
scope: path
parameters: { location: uri, match: present }
---

# VERSION001 — Version appears in the URI

## Intent

This rule identifies an API that encodes its interface version in a resource
URI, such as `/v2/customers`. URI versioning is easy for clients and operators
to see and can support parallel contract versions, but it duplicates resource
identities and can complicate evolution and caching. Whether URI versioning is
allowed is a policy choice, so a finding is a candidate for review rather than
proof of an API defect.

## Detection

The rule has `path` scope and uses the `versioning` detector with:

```yaml
parameters: { location: uri, match: present }
```

For each OpenAPI path, the detector matches the complete path against this
case-sensitive pattern:

```text
.*/(v[0-9]+|version[0-9]+)(/.*)?
```

In practical terms, a path is recognised when one of its slash-delimited
segments is exactly `v` followed by one or more digits, or `version` followed
by one or more digits. The version segment may appear anywhere in the path,
including at the start or end. Examples include `/v1/customers`,
`/customers/v2`, and `/api/version3/customers`. The segment must be lowercase
and must end after the digits or be followed by another slash; `/V1/customers`,
`/v1beta/customers`, and `/version/latest` do not match.

Every matching path produces a finding with the path pointer and the message
`Interface version is exposed through uri`.

## Review-candidate example

This OpenAPI path contains the recognised `v1` segment and therefore produces
a VERSION001 finding:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /v1/customers:
    get:
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema: { type: object }
```

The request URI on the wire would be versioned as follows:

```http
GET /v1/customers HTTP/1.1
Host: api.example.test
Accept: application/json
```

The `info.version` value documents this OpenAPI document and is not inspected
by VERSION001; the path is what causes the finding.

## Compliant example

This path has no segment matching the detector’s pattern, so it does not
produce a VERSION001 finding:

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

An API can be compliant with VERSION001 while still using another versioning
location. For example, a header or versioned media type is evaluated by
VERSION002 or VERSION003, not by this rule.

## Parameters, references, and limitations

`location` must be `uri` for this rule. The detector’s `match: present` branch
reports all matching paths. `match: absent` is supported by the detector for
VERSION004’s API-level absence check, but is not this rule’s configured
behavior.

The detector examines the host’s normalised path facts. It does not resolve a
version from a server URL, server variable, path parameter value, query
parameter, header, media type, payload field, example, or description. A
`$ref` affects this rule only insofar as the host resolves it into the path
facts; an incomplete specification or unusual URL representation can lead to
missing evidence.

The matching is intentionally heuristic and does not validate semantic
versioning, compare versions, verify that all paths use the same version, or
determine whether the segment is actually an interface version rather than a
resource identifier that happens to look like one. Uppercase and custom
conventions are not recognised. Findings should therefore be reviewed in the
context of the API’s versioning policy.
