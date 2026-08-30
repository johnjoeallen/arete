---
id: api-title
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  suffix:
    type: string
    required: false
  forbidden:
    type: string
    required: false
  case:
    type: enum
    required: false
    values: [title-case]
---

# API-title rule

Checks conventions on `info.title`. A missing or blank title is left to the
metadata rule.

- `suffix` — the title should end with this word (e.g. `API`).
- `forbidden` — comma-separated markers the title's final word should not be
  (e.g. `PoC,Test,WIP`), matched case-insensitively.
- `case: title-case` — every significant word should start with an uppercase
  letter; short connector words (`of`, `the`, …) and tokens of three or fewer
  characters are ignored.
