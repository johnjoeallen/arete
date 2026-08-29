# POC results — Starlark detector language (issue #125)

Status: **spike complete** · Branch: `worktree-starlark-detector-poc`

Proves the [research note](policy-engine-dsl-research.md) recommendation: the
three most regex-heavy detectors, ported to Starlark + RE2/J, produce
**identical** occurrences to the trusted-Groovy runtime, while being safe by
construction.

## What was built

| File | Purpose |
|---|---|
| `generic-policy-validation-plugin/.../star/StarlarkDetectorRuntime.java` | Loads a `detect(api, rule)` Starlark function, deep-converts the host model to **immutable** Starlark values, runs it with a hard step cap, normalises the result. |
| `.../star/StarlarkBuiltins.java` | The entire extra capability surface: `re_fullmatch`, `re_search` (RE2/J), `tokenize` (Groovy `String.tokenize` semantics), `url_host`. |
| `src/test/resources/star-poc/{resource-path,operation-semantics,versioning}.star` | Line-for-line ports of the Groovy detectors. |
| `src/test/java/.../StarlarkDetectorPocTest.java` | Runs Groovy vs Starlark for the same rules/specs; asserts equal `(pointer, path, message)` rows. Plus a hostile-input suite. |

Dependencies added: `com.eed3si9n.starlark:starlark:4.2.1` (community repackage
of Bazel's `net.starlark.java`, used only for the spike) and
`com.google.re2j:re2j:1.7`.

## Results

- **Parity**: `StarlarkDetectorPocTest` — 5/5 green. `resource-path`
  (REST001/003/004), `operation-semantics` (HTTP001/002/003/006/008), and
  `versioning` (VERSION001–004) match the Groovy oracle exactly, including the
  empty-result and multi-occurrence cases.
- **Full module suite**: 18/18 green — the existing 13 Groovy tests are
  unaffected.
- **Safe by construction** — each of these is rejected, as a language
  property rather than a filtered call:
  - `load(...)` — no module loading
  - `open("/etc/passwd").read()` — no I/O (name simply unbound)
  - `str(type(api))` — no runtime introspection
  - `1 // 0` — surfaces as a `DetectorScriptException`, not a host crash
  - `for i in range(100000000)` — hits `setMaxExecutionSteps`, deterministic
- Recursion is disallowed by default; the input model is deep-immutable so a
  detector cannot mutate `api`.

## Port notes (Groovy → Starlark)

- `collectMany{…}` / `findAll{…}` → nested `for` + list-comprehension `if`;
  reads about the same length.
- `def matches = { … }` nested helper closures → module-level `def _matches(…)`
  with parameters passed explicitly (no closure-over-locals).
- `==~ /re/` (anchored) → `re_fullmatch(r"re", s)`. Inline `(?i)` mid-pattern
  hoisted to the front — equivalent for these patterns and cleaner.
- Groovy `String.tokenize('/')` **drops empty tokens**; Python-style `split`
  keeps them, hence the `tokenize` builtin.
- `x ?: y` → `x or y` (same truthiness for our string/None cases).
- No detector needed dates, arithmetic beyond compare, or object
  construction — the `new URI(url)` in the hostname detector becomes the
  `url_host` builtin (no `OpenApiMapAdapter` change).

RE2/J covered every pattern in the three detectors (`\b`, `(?i)`,
alternation, non-capturing groups, `{2,}`). No backreferences or lookaround
were used — and both stay unavailable, which is the point.

## Caveats / not done

- `com.eed3si9n.starlark:starlark:4.2.1` bundles **Guava 27.1 (2019)**.
  Production must vendor a current `net/starlark/java/**` from upstream Bazel
  (issue #125) — the spike deliberately took the fast path.
- No timeout wrapper yet (step cap only); no compiled-`Program` cache; the
  runtime returns plain maps rather than wiring into `ValidationResult`.
- Only 3 of 17 detectors ported. The remaining 14 use a strict subset of what
  these three exercise (the `naming`/`schema` ones are simpler), so no new
  blocker is expected — but that is the next step, not a proven fact.

## Recommendation

Proceed with issue #125 as written. The spike found no blocker: Starlark
expresses the detectors cleanly, RE2/J removes the ReDoS concern, and the
capability surface is a 4-function file instead of a two-layer sandbox to
keep correct forever.
