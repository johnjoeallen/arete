# A clearer shape for Distill matchers

> Discussion note. **Nothing here is implemented.** It proposes how matchers
> could be structured to read better, in rough order of value vs. risk.

## The problem

Several matchers — `text-style`, `schema`, `response-code`, `parameter`,
`media-type` — are *grab bags*: one matcher hosts many unrelated checks,
selected by which `rule.parameters[...]` key a rule sets. That forces a shape
that is genuinely hard to read:

1. **The filter is a hand-rolled switch.** `schema/Matcher.dsl` is a cascade of
   `rule.parameters["x"] == "y" ? <cond> : rule.parameters["x2"] == "y2" ? …`
   with `? false` sentinels — an inverted `switch` expressed as nested
   ternaries.
2. **The message is a second, parallel switch** over the same parameters. The
   two must be kept aligned by hand; add a check and you edit two places that
   don't sit near each other.
3. **`rule.parameters["…"]` is repeated** 5–15 times per matcher.
4. **No binding.** `response-code` computes `parseInt(resp.status, -1)` four
   times; `text-style` computes `op.summary.trim()` six times and
   `op.method + " " + path.path` twice.
5. It is **one expression**, so a multi-check matcher grows into a Christmas
   tree of nested `?:` and `.expand { … .expand { … } }`.

The condition that triggers an occurrence and the message that describes it
are never next to each other.

## Model A — one matcher per check  *(no language change)*

Split the grab bags. Each rule that today shares `text-style` gets its own
tiny matcher:

```distill
distill(api, rule) {
    return api.operations
        .filter { op -> !(op.summary is blank) && op.summary.trim().endsWith(".") }
        .map { op -> occurrence(op.pointer, op.method + " " + op.path,
            "Operation summary ends with a period") };
}
```

Keep parameterisation **only** where a matcher is genuinely one check with a
knob — `naming` (a configurable convention regex), `schema-name` (a pattern),
`path-count` (`maximum`). Orthogonal checks bundled under one id (`text-style`
= 6 checks, `schema` = ~12) get split.

Pairs well with an adapter change: expose **`api.operations`** as a flat list
(`method`, `path`, `pointer`, `summary`, `responses`, `parameters`, …) so
matchers stop writing `api.paths.expand { p -> p.operationDetails.expand { … } }`
every time.

- **Pros:** every matcher is 3–6 lines and reads top-to-bottom; message sits
  on the check; no switch; no inversion. The bundle already has 14 dsl-only
  single-purpose matchers — this is the house style, just applied consistently.
- **Cons:** more files; the "iterate operations that have a summary" preamble
  repeats (cheap, and explicit).
- **Risk:** low — pure bundle refactor, the 300-combo Groovy parity sweep
  catches any drift.

## Model B — colocated checks  *(small language change)*

For the few matchers that legitimately host several *related* checks, make
each check a `(condition, message)` pair in a flat list:

```distill
distill(api, rule) {
    return api.operations.expand { op -> op.summary is blank ? [] : [
        flag(!(op.summary ==~ /[A-Z].*/),      op.pointer, op.method + " " + op.path,
             "Summary does not begin with a capital letter"),
        flag(op.summary.trim().endsWith("."),  op.pointer, op.method + " " + op.path,
             "Summary ends with a period"),
        flag(size(words(op.summary)) < 3,      op.pointer, op.method + " " + op.path,
             "Summary has fewer than three words"),
    ] };
}
```

`flag(cond, pointer, path, message)` → an `occurrence` when `cond` is true,
else `null`; **the engine drops `null` entries** from the returned list (one
line relaxed in `castDiagnostics`). No inverted `!(a || b || …)`, no parallel
message ternary, checks are order-independent, adding one is a single line.

For the parameter-selected variant, the parameter guard just lives inside the
condition, next to its message:

```distill
flag(rule.parameters["trailing-period"] == "present" && op.summary.trim().endsWith("."),
     op.pointer, loc, "Summary ends with a period"),
```

- **Risk:** low–moderate — one builtin, one relaxed invariant (`null` in the
  result list is currently an error). Parity tests still gate it.

## Model C — a binding form  *(small language change)*

Kill the recomputation. Distill closures already bind a name; `let` / `with`
is sugar over that:

```distill
distill(api, rule) {
    return api.operations.expand { op ->
        let loc = op.method + " " + op.path;
        let summary = op.summary is blank ? "" : op.summary.trim();
        [ flag(summary.endsWith("."), op.pointer, loc, "…"), … ] };
}
```

or, without new grammar, a `bind(value) { name -> body }` builtin
(= `[value].map { name -> body }` with the list unwrapped). Lower priority —
worth doing once A/B land and the remaining matchers show where it hurts.

## `schema`'s nested-ternary filter

Even with no other change, `schema`'s `cond ? false : cond ? false : … : true`
is an **AND of guards** written the hard way. `.filter { … }.filter { … }`
chains, or an `all([c1, c2, c3])` helper, would express the same thing
readably.

## Recommendation

1. **Model A** — split the grab bags, add `api.operations`. Biggest win, no
   language risk. Most grab-bag matchers disappear.
2. **`flag()` + null-dropping** (Model B) for the handful that stay
   multi-check.
3. **`let` / `bind`** (Model C) last, guided by what's left.

The Groovy parity suite (117 curated + 300-combo sweep) is the safety net for
each rewrite; `words()`-style additions have already gone through it cleanly.
