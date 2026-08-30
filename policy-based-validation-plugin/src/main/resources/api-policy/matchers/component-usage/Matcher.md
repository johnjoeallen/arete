---
id: component-usage
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [unreferenced-schema]
---

# Component-usage rule

Compares the declared reusable components against the `$ref` targets found in
the raw document (`api.lint.refs`).

- `unreferenced-schema` — reports a `components/schemas` entry that no `$ref`
  in the document points at. An unreferenced schema is usually dead weight or
  a sign of a broken reference.

The check is textual: it matches `#/components/schemas/<name>` against the
collected `$ref` strings and does not resolve references itself.
