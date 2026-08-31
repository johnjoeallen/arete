# A clearer shape for Distill matchers

> **Status: implemented** (Option 0 below). See the
> [Distill reference](../docs/validation/distill.md#repeatable-filtermap-checks)
> for the shipped feature and [History](../docs/history.md#the-matcher-language)
> for the rationale in context. This note is kept for the design trail.

## The problem

Several bundled matchers (`text-style`, `schema`, `response-code`, `parameter`,
`media-type`) run a handful of unrelated checks over one collection, selected
per rule by `rule.parameters`. As a single nested `filter`/`map` expression
this forced:

- an inverted `!(passA || passB || …)` filter predicate,
- a separate, parallel `? :` message cascade that had to stay aligned with it,
- `rule.parameters["x"]` repeated 5–15 times,
- one giant nested expression with the triggering condition and its message
  far apart.

## What shipped — Option 0: `checks(source) { filter{}.map{}, … }`

```distill
distill(api, rule) {
  return checks(api.operations.filter { it.summary != null && it.summary.trim() != "" }) {
    filter { rule.parameters["trailing-period"] == "present" && it.summary.trim().endsWith(".") }
      .map { occurrence(it.pointer, it.method + " " + it.path,
                        "Operation summary ends with a period") },
    filter { rule.parameters["minimum-words"] != null
             && count(words(it.summary)) < rule.parameters["minimum-words"] }
      .map { occurrence(it.pointer, it.method + " " + it.path,
                        "Operation summary has too few words to be meaningful") }
  };
}
```

- `checks(<source>) { … }` — one new construct. The source is evaluated once
  and bound for the block; each comma-separated stanza is a bare
  `filter { } .map { }` chain rooted at it (no receiver token); the stanzas'
  occurrences concatenate.
- Closure parameters became optional — the item is `it`.
- Flat `api.operations`, `api.responses`, and `api.schemaProperties` model
  views were added, each element carrying its own location, so a stanza needs
  no outer `expand`.
- `size(list)` builtin renamed `count(list)`.
- No statements, no new file format; still one expression. The Groovy parity
  suite gated the change; `text-style`, `schema`, and `media-type` migrated so
  far. `parameter`, `response-code`, and the remaining `check`-dispatch
  matchers are candidates but their branches emit per-sub-element (per
  parameter, per response header) and need their own `expand`, so `checks` is
  a poorer fit there.

### Semantic notes

The old grab-bag contract was "a rule that supplies multiple parameters
matches only summaries that satisfy **all** of them, reported with one
message." Under `checks` each satisfied stanza reports independently. No
bundled rule supplies more than one check parameter, so bundle behaviour is
unchanged; `Matcher.md` for a migrated matcher states the new behaviour.

The migration also removed the fall-through behaviour where a matcher given no
recognised check parameter flagged **every** subject with a "matches the
configured rule" message — an occurrence for a compliant subject. Migrated
matchers (and their Groovy parity oracles) now produce nothing in that case,
and unused `present`-style parameter modes that only reached that fall-through
were deleted from the descriptors.

## Options considered and rejected

- **Split one matcher per check** (+ `api.operations` adapter). More files;
  didn't address the shape of the multi-check matchers that remained.
- **`flag(cond, pointer, path, message)` returning occurrence-or-null**, engine
  drops nulls. Needed the result list to carry nulls; still a pipeline with the
  condition split from the emit.
- **`let name = expr;` / `bind(value) { name -> body }`**. A general binding
  form — more language than the problem needed.
- **Bounded statements (`for … { flag when … }`)** and **matcher-as-check-table
  (YAML front matter)**. Both fix readability but move the language much
  further than `checks` does.
