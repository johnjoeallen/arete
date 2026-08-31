---
id: text-style
language: distill
source: Matcher.dsl
scopes:
  - operation-summary
parameters:
  initial-capital:
    type: boolean
    required: false
  convention:
    type: enum
    required: false
    values:
      - sentence-case
  trailing-period:
    type: enum
    required: false
    values:
      - present
  maximum-length:
    type: integer
    required: false
  minimum-words:
    type: integer
    required: false
  maximum-word-length:
    type: integer
    required: false
  match:
    type: enum
    required: false
    values:
      - non-action-oriented
  action-prefixes:
    type: string
    required: false
---

# Text-style rule

Inspects an operation summary using a deliberately small set of mechanical
style checks. It does not infer business meaning or judge the active policy.
A rule selects one check using the declared parameters. The matcher is a
`checks(...)` block — one `filter`/`map` stanza per check — so a rule that
supplies several parameters reports each satisfied check independently rather
than only summaries that satisfy all of them. No bundled rule supplies more
than one. A rule that supplies none of the check parameters produces nothing.

With `match: non-action-oriented`, `action-prefixes` overrides the recognised
verb list — a comma-separated string (default
`Get,List,Create,Update,Delete,Replace,Search,Find,Cancel,Activate,Deactivate`).
The list is spliced into the check's regex with a `{{ ... }}` interpolation
hole.

The rule ignores operations without a summary. `DOC001` owns the distinct
condition that a summary is missing.
