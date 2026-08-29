---
id: VERSION002
category: Versioning
detector: versioning
scope: header
parameters: { location: header, match: present }
---

# VERSION002 — Version appears in a header

## Intent

This rule identifies an API that selects an interface version through an HTTP
header. Header-based versioning can keep resource URLs stable while allowing
clients to request a particular contract. It can also make caching,
observability, and client configuration less obvious. Whether that trade-off
is acceptable is a policy choice, so a finding is a candidate for review
rather than proof of an API defect.

## Detection

The rule has `header` scope and uses the `versioning` detector with:

```yaml
parameters: { location: header, match: present }
```

For each path, the detector examines the normalised header parameters of all
its operations. It reports the path when any operation has a parameter whose
`in` value is `header` and whose name, matched case-insensitively, is exactly
one of:

* `version`;
* `api-version`, `api_version`, or `apiversion`; or
* `x-api-version`.

The finding points to the path and has the message `Interface version is
exposed through header`. It does not report the parameter’s JSON Pointer or
the value clients are expected to send.

## Review-candidate example

This OpenAPI operation declares `X-API-Version`, so VERSION002 reports a
finding for `/customers`:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /customers:
    get:
      parameters:
        - in: header
          name: X-API-Version
          required: false
          schema:
            type: string
            enum: ['1', '2']
      responses:
        '200':
          description: OK
```

The corresponding request might look like this:

```http
GET /customers HTTP/1.1
Host: api.example.test
X-API-Version: 2
```

The rule reports the declaration in the specification; it does not inspect
live requests. A header named `Version` or `api_version` is also recognised.

## Compliant example

This operation declares no recognised version header, so it does not produce
a VERSION002 finding:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /customers:
    get:
      parameters:
        - in: header
          name: X-Request-ID
          schema: { type: string }
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema: { type: object }
```

The absence of this finding does not mean the API is unversioned. URI
versioning and media-type versioning are checked independently by VERSION001
and VERSION003, and VERSION004 checks for the absence of all three recognised
mechanisms.

## Parameters, references, and limitations

`location` must be `header` for this rule. The detector’s `match: present`
branch reports every matching path. `match: absent` is supported by the
detector for VERSION004’s API-level check, but is not this rule’s configured
behavior.

The detector uses the host’s normalised operation parameter facts. A
referenced parameter is considered only if the host resolves it into those
facts; an unresolved or incomplete `$ref` can therefore cause a false
negative. Header parameters declared only by an external gateway or runtime
middleware are invisible to this OpenAPI-based check. The detector also does
not infer headers from descriptions, examples, security schemes, or arbitrary
extension fields.

VERSION002 does not inspect `info.version`, URI segments, media types, query
parameters, cookies, payload fields, response headers, or the actual header
values exchanged at runtime. It does not validate the version format,
requiredness, allowed values, negotiation semantics, or consistency across
operations. Header-name recognition is deliberately heuristic; custom
conventions such as `X-Contract-Revision` are not matched and should be
reviewed using an appropriate policy or detector.
