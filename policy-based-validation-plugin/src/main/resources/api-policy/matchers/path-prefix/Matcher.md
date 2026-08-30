---
id: path-prefix
language: distill
source: Matcher.dsl
scopes: [api]
parameters: {}
---

# Path-prefix rule

Reports when **every** declared path begins with the same literal first
segment (ignoring `{template}` parameters), and there is more than one path.
A shared, non-varying prefix is usually a base path that belongs in the
server URL rather than repeated on every path.

The rule emits a single occurrence for the whole document at `/paths`.
