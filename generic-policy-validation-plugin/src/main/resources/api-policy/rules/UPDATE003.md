---
id: UPDATE003
category: Update semantics
detector: manual
scope: operation
---

# UPDATE003 — Update could be represented as a sub-resource

## Intent

An independently updateable concept may sometimes be clearer when represented
as a sub-resource. Giving that concept its own resource identity can make its
lifecycle, permissions, and representation easier to understand than exposing
it only as a field of a larger resource. The best design depends on domain
boundaries and client workflows, so this rule is a human design review prompt,
not a mechanically provable violation.

## Detection and scope

The rule has `operation` scope and uses the `manual` detector:

```yaml
id: UPDATE003
category: Update semantics
detector: manual
scope: operation
```

It has no parameters. The manual detector intentionally returns no automated
occurrences for any OpenAPI document. Consequently, running validation does
not create an UPDATE003 finding at an operation pointer, even when an
operation looks like an update of a nested concept. The rule remains in the
policy catalogue as guidance for a reviewer who is assessing update design.

## Review example

The following operation should prompt a reviewer to ask whether notification
settings are an independently addressable resource:

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
paths:
  /customers/{customer_id}/notification-settings:
    put:
      summary: Update notification settings
      parameters:
        - in: path
          name: customer_id
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                email_enabled: { type: boolean }
      responses:
        '204': { description: Updated }
```

This is not labelled as a detector violation. Reviewers should consider:

* Is `notification-settings` independently created, read, replaced, or
  deleted?
* Does it have its own identity, lifecycle, authorization, or concurrency
  rules?
* Would clients benefit from addressing it directly, for example at
  `/notification-settings/{customer_id}`?
* Does the nested URL better communicate ownership, or does it merely add
  another route without clarifying the domain model?

## Alternative representation

If the concept is independently addressable, an API might instead expose a
top-level resource rooted by the customer identifier:

```yaml
paths:
  /notification-settings/{customer_id}:
    put:
      summary: Replace notification settings
      parameters:
        - in: path
          name: customer_id
          required: true
          schema: { type: string }
      responses:
        '204': { description: Replaced }
```

Neither URL shape is universally compliant. The alternative is an example of
a design to compare during review, not a required rewrite.

## Configuration, references, and limitations

Because this rule uses the manual detector, there are no rule parameters,
matching modes, severity thresholds, or configuration switches that change
its behavior. References, missing descriptions, HTTP methods, path depth, and
request or response schemas do not cause the rule to report an occurrence.

An OpenAPI document cannot reliably establish whether a concept is
independently updateable, whether a PUT replaces the whole concept, or how
clients and the server behave at runtime. The detector therefore does not
infer semantics from verbs, path names, nesting, schemas, summaries, or JSON
payloads. Use the operation documentation and domain knowledge to resolve the
question; automated rules such as UPDATE001–002 address different update
semantics and should not be treated as substitutes for this review.
