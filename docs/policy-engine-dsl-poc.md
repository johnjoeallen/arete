# Starlark detector port (issue #125)

Status: **all 22 detectors ported and parity-verified**

Realises the [research note](policy-engine-dsl-research.md): every bundled
detector, rewritten in Starlark + RE2/J, produces **identical** occurrences to
the trusted-Groovy runtime, while being safe by construction.

## What was built

| File | Purpose |
|---|---|
| `generic-policy-validation-plugin/.../star/StarlarkDetectorRuntime.java` | Loads a `detect(api, rule)` Starlark function, deep-converts the host model to **immutable** Starlark values (int/float/bool/str/list/dict), runs it with a hard step cap, normalises the result to occurrence maps. |
| `.../star/StarlarkBuiltins.java` | The entire extra capability surface: `re_fullmatch`, `re_search` (RE2/J), `tokenize` (Groovy `String.tokenize` semantics), `parse_int`, `url_host`. |
| `.../api-policy/detectors/*/Detector.star` (×22) | Ports of every `Detector.groovy`, sitting next to the originals in the bundle. |
| `.../StarlarkParityTest.java` | Drives the whole bundle the way `GenericPolicyValidationPlugin.validate` does — every policy, every disposition, effective (rule + policy-override) params, plus a direct per-rule sweep — over a 10-spec messy corpus, asserting Groovy and Starlark occurrence lists are byte-identical. |

Dependencies added: `com.eed3si9n.starlark:starlark:4.2.1` (community repackage
of Bazel's `net.starlark.java`, spike-only) and `com.google.re2j:re2j:1.7`.

## Results

- **Parity: all 22 detectors, 0 mismatches.** The sweep runs >900 (spec ×
  policy × rule) comparisons plus a per-rule pass. Every detector except the
  two intentionally-empty ones (`manual`, `compatibility`) is exercised
  non-vacuously — the test fails if any detector is only checked on empty
  output.
- **Full module suite: 24/24 green** — the 21 existing Groovy tests are
  untouched.
- **Safe by construction** — rejected as language properties, not filtered
  calls: `load(...)`, `open("/etc/passwd")`, reflection/introspection,
  `1 // 0` (→ `DetectorScriptException`), `for i in range(1e8)` (step cap).
  Recursion disallowed; the input model is deep-immutable.

## Port notes (Groovy → Starlark)

- `collectMany{…}` / `findAll{…}` → nested `for` + comprehension `if`.
- `def x = { … }` nested helper closures → module-level `def _x(…)` with
  params passed explicitly (Starlark has no closure-over-locals in module fns).
- `switch` → `if`/`elif` chains, usually extracted to a `_message()` helper.
- `==~ /re/` → `re_fullmatch(r"re", s)`; `(x =~ /re/).find()` → `re_search`.
  Inline `(?i)` hoisted to the front. **Deliberate quirks preserved**: three
  Groovy regexes carry doubled backslashes (`\\b`, `\\.`) from slashy-string
  escaping and so never match as intended — the ports keep the exact same
  literal so results match (`metadata` semver, `text-style` all-caps,
  `response-code` semantic-conflict).
- `String.tokenize('/')` drops empty tokens → `tokenize` builtin (Python
  `split` keeps them).
- `x ?: y` → `x or y`; `?.` → `dict.get()` / explicit `!= None`.
- Enum value type checks (`v instanceof Integer/Number/String`) → `type(v)`
  after the runtime converts Java numbers to `StarlarkInt` / `StarlarkFloat`.
- `Integer.parseInt` + catch → `parse_int(s, fallback)` builtin.
- `new URI(url).host` → `url_host` builtin — **no `OpenApiMapAdapter` change**.

RE2/J accepted every pattern in the bundle (`\b`, `(?i)`, alternation,
`(?:)`, `{n,}`, character classes). No backreferences or lookaround are used,
and both stay unavailable.

## Wired in

- The engine **runs Starlark by default.** `PolicyBundleLoader` loads
  `Detector.star` for every detector; `GenericPolicyValidationPlugin`
  dispatches per `detector.language()`.
- **Groovy is supported but disabled by default** (not deprecated) — it is
  unsandboxed, so it stays off until the sandbox plan lands, then returns as a
  first-class option. Opt back in with `detector-language=groovy` (config) or
  `-Dspeculate.policy.detector-language=groovy`; the plugin logs a warning.
  `PolicyBundleLoader.LoadOptions(forceGroovy)` drives it. A detector missing
  its `.star` falls back to Groovy with a per-detector warning.
- `StarlarkDetectorRuntime` moved into `com.speculate.validation.policy` and
  returns `List<Occurrence>` / throws `DetectorException` like the Groovy one.

## Not done (follow-ups on issue #125)

- **Vendor Starlark.** `com.eed3si9n.starlark:starlark:4.2.1` bundles Guava
  27.1 (2019) and pulls a JNI CPU-profiler stub that logs a native-access
  warning. Production vendors a current `net/starlark/java/**` (runtime subset
  only — exclude `cmd`, `JNI`, `CpuProfiler`).
- Implement the [sandbox plan](policy-engine-sandbox-plan.md) Layers A + B so
  the Groovy runtime can be re-enabled as a first-class option; Layer C + §10
  additionally gate remote bundle loading.
- Timeout wrapper (step cap only today); compiled-`Program` cache.
