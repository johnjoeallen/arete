---
id: path-syntax
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [no-query, no-fragment]
---

# Path-syntax rule

Checks the syntax of the path keys under `paths`.

- `no-query` — reports a path key containing `?`. Query parameters belong in
  the operation's `parameters` list with `in: query`, not baked into the path
  string, where routers and doc tools will not interpret them.
- `no-fragment` — reports a path key containing `#`.
