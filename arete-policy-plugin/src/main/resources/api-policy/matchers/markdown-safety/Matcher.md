---
id: markdown-safety
language: distill
source: Matcher.dsl
scopes: [api]
parameters: {}
---

# Markdown-safety rule

Scans every `description` and `summary` string in the document (`api.descriptions`)
for active markup that becomes dangerous once the prose is rendered as HTML:

- a `<script>` open or close tag,
- a `javascript:` URL,
- an inline event-handler attribute (`onload=`, `onerror=`, `onclick=`, …),
- an `eval(` call.

One occurrence is emitted per offending field, at that field's pointer. The
rule is a defence-in-depth check for API portals that render OpenAPI
descriptions as Markdown/HTML.
