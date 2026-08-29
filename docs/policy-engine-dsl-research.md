# Research — a restricted detector language instead of a Groovy sandbox

Status: **research** · Companion to
[`policy-engine-sandbox-plan.md`](policy-engine-sandbox-plan.md)

The sandbox plan hobbles a general-purpose language (Groovy) down to a safe
subset with two enforcement layers plus, for remote bundles, a worker
process. This note asks the opposite question: **what if the detector
language were safe by construction** — small enough that there is nothing to
sandbox because the interpreter simply cannot express I/O, reflection, or
unbounded computation?

## 1. What detectors actually need

Inventory of every operation used across the 17 bundled `Detector.groovy`
scripts:

| Category | Operations actually used |
|---|---|
| **List** | `collect` (map), `collectMany` (flatMap), `findAll` (filter), `find`, `any`, `count`, `each`, `size`, `last`, `toSet`, `contains`, `isEmpty`, list literals, append (`<<`) |
| **Map** | key access (`m.k`, `m['k']`), `containsKey`, safe-nav `?.`, map literals `[pointer:…, path:…, message:…]`, entry iteration |
| **String** | `toLowerCase`, `trim`, `startsWith`, `endsWith`, `contains`, `length`, `charAt`, `split`, `tokenize`, `+` concat, `==`, `equalsIgnoreCase`, `toString` |
| **Regex** | full match (`==~`), partial match (`=~ … .find()`), case-insensitive `(?i)`, `\b`, **patterns built dynamically from rule parameters** |
| **Number** | `Integer.parseInt` (guarded), comparisons `>= < >` |
| **Char** | `Character.isUpperCase` |
| **Types** | `instanceof String/Integer/Number/Map/List`, `as Boolean`, `as Set` |
| **Control** | ternary, elvis `?:`, safe-nav, `switch`, `if`, `&& || !`, early `return`, one `try/catch` (around `parseInt`) |
| **Abstraction** | local `def` bindings; **named local closures** that call each other (`def matches = { … }`, `def conforms = { … }`); closures passed to list ops |

What detectors **never** need: loops, mutation of the input, recursion, dates,
object construction (except the one `new URI(url)` — §5 of the sandbox plan),
file/network/env/reflection, threads, arbitrary Java classes.

So the target language is a **pure, total transformation language over
JSON-shaped values with first-class lambdas, higher-order list operations,
string functions, and regex.** This is a well-known, well-bounded category.

## 2. Options considered

### Adopt an existing safe language runtime (JVM)

| Runtime | Safe by construction? | Fit for detectors | Regex | Verdict |
|---|---|---|---|---|
| **Starlark** (`net.starlark.java`, extracted from Bazel) | **Yes** — no I/O, no `import`, bounded steps + memory, deterministic | **Excellent** — lists, dicts, comprehensions, `def` functions, string methods; current Groovy reads almost 1:1 | Not built-in — add a host builtin (`re.fullmatch`, `re.search`) | **Top pick** |
| **CEL** (`dev.cel:cel`, Google) | **Yes** — non-Turing-complete, designed for policy eval | **Weak** — single-expression; no in-language named sub-predicates; multi-branch message building and the `def matches`/`def conforms` pattern force every detector to be restructured into one nested macro | `matches()` only; partial match & dynamic patterns workable | Rejected on fit |
| **Jsonnet** (`sjsonnet`, JVM) | **Yes** — hermetic, deterministic, `import` disable-able | **Good** — `function(api, rule)`, `std.map/filter/foldl`, string funcs | `std.regex*` parity across the JVM port is shaky | Backup |
| **JEXL3** (`commons-jexl3`) | **No** — it invokes Java methods; safety is a `JexlSandbox` allowlist | Good | `java.util.regex` | Rejected — same "sandbox a general engine" problem, just smaller |
| **MVEL / SpEL** | **No** — full Java access; SpEL sandboxing is historically leaky | — | — | Rejected |
| **Rego / OPA** | Yes | Good semantically | n/a | Rejected — no first-class embeddable JVM runtime; operationally heavy |
| **JSONLogic / JMESPath** | Yes | Too weak — can't build the computed `message` strings or the multi-condition predicates cleanly | limited | Rejected |

### Build a tiny expression language ("SPQL" — Speculate Policy Query Language)

A hand-rolled Pratt parser + tree-walking evaluator over a fixed value model
(`null`, bool, number, string, list, map, lambda). Closed grammar:

- literals, `.field` / `[expr]` access, `f(args)` calls, `x -> expr` lambdas,
  `if c then a else b`, `let x = e in …`, `&& || !`, comparisons, `+` (concat
  / numeric only);
- a **fixed builtin set**: `filter map flatMap find any count size keys values
  contains startsWith endsWith lower trim split tokenize fullmatch search
  parseInt` — nothing else, ever, without a code change;
- **no** loops, assignment, recursion, or user-defined top-level functions
  (lambdas only, non-recursive).

Cost: ~1.5–2.5k LOC incl. tests. Benefits: total control of semantics, error
messages, and every resource limit (AST node cap, evaluation-step cap, output
cap, string-length cap); zero third-party language runtime; the builtin set
*is* the security boundary and extending it is an explicit, reviewed act.
Downside: a language to own, document, and provide test tooling for;
contributors must learn it.

## 3. The regex problem (applies to every option)

Nearly every detector uses regex, several **build the pattern from rule
parameters**, and rule parameters will come from untrusted/remote bundles.
Requirements: full-match + partial-match, `(?i)`, `\b`, dynamic construction —
and **ReDoS resistance**, because `java.util.regex` allows catastrophic
backtracking on an attacker-chosen pattern *or* input.

**Use `com.google.re2j`** (pure-Java RE2) behind whatever regex builtin the
language exposes: linear-time guaranteed, no catastrophic backtracking. It
lacks backreferences and lookaround — neither is used by any current detector,
and both should stay unavailable. This single choice removes an entire class
of DoS that the Groovy-sandbox path would still have to mitigate separately
(timeout only).

## 4. How this interacts with the sandbox plan

A restricted language **replaces Layers A and B** of the sandbox plan
outright — there is no Groovy, no `SecureASTCustomizer`, no
`GroovyInterceptor`, no allowlist to keep correct.

Still needed from that plan:

- **§4 execution hardening** — timeout, output caps, `catch (Throwable)`,
  per-detector compiled-form cache. (Smaller: a total language with step
  caps barely needs a timeout, but keep one as a backstop.)
- **§9 Layer C (worker process)** — *reduced but not eliminated*. A pure
  interpreter with hard step/memory caps is a far weaker RCE target than
  Groovy, so out-of-process isolation may become optional rather than a hard
  gate for remote loading. Decide after a threat review of the chosen
  interpreter.
- **§10 supply chain** — unchanged. Signature/provenance/pinning is
  orthogonal to the execution model.

It also dissolves **§5** (the `new URI(url)` carve-out): expose a
`url_host(s)` builtin and the hostname detector needs no JDK type and no
`OpenApiMapAdapter` change.

The `Detector.md` descriptor already carries `language: groovy`. Add
`language: starlark` (or `spql`) and migrate detectors incrementally; the
loader picks the runtime per descriptor.

## 5. Migration cost

All 17 detectors are rewritten under *either* the sandbox plan (to the safe
Groovy subset, and re-verified against the interceptor) or this plan (to the
new language). The existing tests assert exact occurrence counts and scores
per detector and are the oracle for both.

- **→ Starlark**: near-mechanical. `collectMany{…}` → list comprehension or
  `[x for … for …]`; `findAll` → `filter` / comprehension `if`; `def matches
  = { … }` → a nested `def`. The one `try/catch` around `parseInt` → a
  builtin that returns a sentinel.
- **→ SPQL**: similar shape, more verbose (`let … in`), fewer surprises.
- **→ safe Groovy subset** (sandbox plan): smallest diff, but the result is
  still Groovy and still needs Layers B+C forever.

## 6. Recommendation

1. **Prefer adopting Starlark** (`net.starlark.java`) + `re2j`-backed regex
   builtins + a handful of string builtins (`tokenize`, `url_host`). It is
   safe by construction, deterministic, resource-bounded out of the box,
   battle-tested (Bazel), and the detectors stay readable. This removes
   Layers A+B entirely and likely downgrades Layer C from "hard gate" to
   "optional".
2. **Fall back to a hand-rolled SPQL** only if Starlark's footprint,
   semantics, or step-limit model prove unworkable — the operation set is
   small and closed enough that a ~2k-LOC interpreter is a bounded,
   one-time cost with total control.
3. **Do not** go the JEXL/MVEL/SpEL route: it keeps the "sandbox a general
   engine" problem this research exists to avoid.

### Suggested next step

Spike: port the 3 most regex-heavy detectors (`operation-semantics`,
`resource-path`, `versioning`) to Starlark + `re2j`, run them against the
existing test specs, and confirm identical occurrence output. That exercises
comprehensions, dynamic patterns, `(?i)`/`\b`, and nested helper functions —
the parts most likely to reveal a blocker. ~1–2 days.
