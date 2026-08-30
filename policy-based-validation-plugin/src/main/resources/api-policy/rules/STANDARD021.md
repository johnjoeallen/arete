---
id: STANDARD021
category: Standards
matcher: document-lint
scope: api
parameters: { check: parser-message, pattern: "(?i)(#/\\S+ is missing|is not of type .?schema|could not resolve|unable to (load|resolve))" }
---

# STANDARD021 — Unresolved reference

Every `$ref` must resolve to a definition that exists in the document (or a
reachable external document). An unresolved reference produces an invalid
contract that generators cannot process.

## Diagnostic

```yaml
paths:
  /customers:
    get:
      responses:
        '200':
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Customer' }   # not defined
```

## Compliant

```yaml
components:
  schemas:
    Customer: { type: object }
```

## Detection and scope

The rule has `api` scope and uses the `document-lint` rule with
`check: parser-message`. It reports each message the OpenAPI parser produced
that matches `pattern` — the parser emits a diagnostic such as
`attribute components.schemas.X.Y is not of type \`schema\`` for an
unresolved reference.

## Configuration and limitations

`pattern` is a policy parameter. The rule surfaces parser diagnostics rather
than resolving references itself, so its precision depends on the parser's
message wording.
