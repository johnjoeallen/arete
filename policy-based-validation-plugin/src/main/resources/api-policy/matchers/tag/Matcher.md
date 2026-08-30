---
id: tag
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  check:
    type: enum
    required: true
    values: [name-convention, documented, declared, unique]
  convention:
    type: enum
    required: false
    values: [camelCase, snake_case, kebab-case, hyphenated]
---

# Tag rule

Inspects the API tags — both the top-level `tags` list and the tag names
referenced by operations.

- `name-convention` — reports a tag name (from any operation) that does not
  follow `convention`.
- `documented` — reports a top-level `tags` entry with no `description`.
- `declared` — reports a tag name used by an operation that is not defined in
  the top-level `tags` list.
- `unique` — reports a top-level `tags` entry whose `name` is the same as an
  earlier entry's. The first entry is left alone; each later duplicate is
  reported.
