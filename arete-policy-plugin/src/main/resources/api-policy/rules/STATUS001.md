---
id: STATUS001
category: HTTP status
matcher: response-code
scope: operation
parameters: { operation-type: create, required-status: 201 }
---

# STATUS001 — Creation operation lacks an appropriate success status

## Intent

Creation operations should document a success status that communicates the
creation outcome. For synchronous creation, `201 Created` tells clients that a
resource was created and can be paired with `Location`. This first-pass rule
uses 201 as the policy requirement; asynchronous creation and its 202 status
are separate design considerations.

## Detection and scope

The rule has `operation` scope and uses the `response-code` rule:

```yaml
parameters: { operation-type: create, required-status: 201 }
```

An operation is treated as a creation operation when its method is POST or
PUT. If none of its documented responses has numeric status 201, the rule
reports the operation at its operation pointer with `Operation lacks the
required documented status`.

## Review-candidate example

This POST is treated as creation but documents only 200:

```yaml
paths:
  /customers:
    post:
      responses:
        '200': { description: Customer created }
```

The contract should either document 201 for synchronous creation or explain a
different, intentional outcome.

## Compliant example

This operation includes 201 and does not match:

```yaml
paths:
  /customers:
    post:
      responses:
        '201':
          description: Customer created
          headers:
            Location: { schema: { type: string, format: uri } }
```

## Parameters, references, and limitations

The rule requires both configured parameters. Status keys are normalised to
integers before comparison, and referenced responses count only when resolved
into the host’s response facts. The rule does not inspect summaries,
request bodies, response descriptions, Location headers, runtime behavior, or
whether PUT really creates a resource. It does not accept 202 for asynchronous
creation in its current contract.
