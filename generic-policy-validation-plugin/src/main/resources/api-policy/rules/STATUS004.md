---
id: STATUS004
category: HTTP status
matcher: response-code
scope: operation
parameters: { operation-type: identifiable-resource-retrieval, required-status: 404 }
---

# STATUS004 — Resource retrieval lacks a not-found response

## Intent

Retrieving an individually identifiable resource should document what happens
when the resource does not exist. A 404 response lets clients distinguish a
missing resource from a successful representation or a server failure.

## Detection and scope

The rule has `operation` scope and uses the `response-code` rule with:

```yaml
parameters: { operation-type: identifiable-resource-retrieval, required-status: 404 }
```

An operation is considered identifiable-resource retrieval when its method is
GET and its path contains `{`. If none of its documented responses has status
404, the rule reports the operation with `Operation lacks the required
documented status`. The test is based on the path template and method, not on
the operation summary or schema.

## Review-candidate example

This GET is reported because it addresses an identified resource but documents
only success:

```yaml
paths:
  /customers/{customer_id}:
    get:
      responses:
        '200': { description: Customer returned }
```

## Compliant example

Adding a 404 response satisfies the rule:

```yaml
paths:
  /customers/{customer_id}:
    get:
      responses:
        '200': { description: Customer returned }
        '404': { description: Customer not found }
```

Collection retrieval at `/customers` is not considered identifiable-resource
retrieval by this rule.

## Parameters, references, and limitations

Both parameters are part of the rule contract. The rule compares status
codes numerically, so equivalent documented integer response keys are
recognised after normalisation. Referenced responses count only when resolved
into the host’s response facts. The rule does not verify the response body,
headers, links, authorization behavior, or whether a server actually returns
404. It also does not infer that a non-GET operation retrieves a resource or
that every path containing braces represents a single resource; unusual path
designs remain review candidates.
