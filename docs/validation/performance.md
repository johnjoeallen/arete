# The case for Distill

Areté evaluates every matcher with [Distill](distill.md). The policy engine
exists so that adding a rule means writing a matcher, not writing a plugin;
Distill is the language those matchers are written in. This page makes the
argument for that design and shows the measurements behind it.

A matcher has to be three things at once:

- **Safe to run from any source.** Matchers are meant to be shared — bundled,
  dropped into `~/.arete/policies/`, and in future pulled from a remote
  source. Running one must not require trusting its author with the host JVM.
- **Changeable without a release.** A new or adjusted rule should be an edit
  to a text file, not a recompile-and-redeploy.
- **Cheap enough to ignore.** A validation runs ~150 matcher evaluations;
  their combined cost must stay small next to the unavoidable OpenAPI parse.

Distill is the design that delivers all three. The two obvious alternatives
each give one of them up, and the numbers below show what that buys — or
doesn't.

## Why not Groovy

Matchers were originally Groovy closures run in-process. Groovy executes
arbitrary code with full JVM access, so it fails the first requirement
outright: a Groovy matcher from an untrusted source can do anything the
application can. It is retained now only as a build-time parity reference —
`Matcher.groovy` files are run against `Matcher.dsl` on every build to catch
any semantic drift, never against a submitted spec.

Groovy is not even the fast option. Compiled once and reused, it is **~4–5×
slower than Distill** across the matcher set (measured below); as it was
actually wired — recompiling the script with a fresh `GroovyShell` on every
call — it was roughly 50× slower again. It offered neither safety nor speed.

## Why not hand-written Java

The other alternative is [Zally](https://github.com/zalando/zally)'s model:
every rule is a compiled Java class. That is genuinely fast — hand-written
Java is **~5–6× faster than Distill** (measured below). But as the unit in
which *rules* are written, it fails the other two requirements:

- A rule whose logic isn't covered by an existing matcher needs a **new Java
  class, a new release, and a redeploy** before anyone can use it.
- Matchers can only ever ship **inside the application jar**. There is no safe
  way to load one from `~/.arete/policies/`, an internal artifact repository
  (Nexus, Artifactory), a Git repository or release asset (GitHub, GitLab, an
  internal host), an HTTPS URL or object store (S3, GCS), an OCI registry, or
  a shared policy service — because a Java class *is* code with full access.

## What Distill gives up, and what it keeps

Distill is code too — but it is not a general-purpose language. A matcher is a
**single expression** that consumes `api` and `rule` and returns
`occurrence(...)` values. The grammar has no statements, no local variables,
no user-defined functions, and no other result type; the interpreter grants
no I/O, reflection, recursion, or unbounded iteration. It is a narrow
data-pipeline processor — `.map` / `.filter` / `.expand`, slashy regex
literals, a fixed builtin set — and can be nothing else.

That narrowness is what makes a matcher **data rather than a program to be
trusted**: it can be loaded from anywhere and run without vetting its author,
because the worst it can do is inspect the spec and hand back a list. The cost
is interpreter overhead — a tree-walk instead of compiled bytecode — which the
rest of this page quantifies.

## Plugins are a separate trust tier

None of this replaces Areté's plugin facility. A validation plugin is
arbitrary compiled Java loaded through the SPI and a child-first classloader;
`policy-based-validation-plugin` is itself one. A plugin runs with full
application privileges, so **installing one is a trust decision** — and
building it from source does not remove that boundary, it only moves the
audit to you: the plugin's own code, its transitive dependencies, and its
build. That trust is appropriate for a plugin, which is deliberately chosen,
versioned, and deployed like any other dependency.

The policy engine was built precisely so that this is the **last plugin most
deployments ever need**. It cannot cover every conceivable check — a rule that
needs data the model does not expose, or logic the pipeline cannot phrase,
still calls for a plugin — but in practice virtually every API-style rule is
expressible as a matcher. The intended path for a new rule is: write a
`Matcher.dsl`, not a Java class; reserve a new plugin for the rare case that
genuinely needs one.

So trust is spent **once, at the plugin boundary**, and not again for every
rule inside it. The policy plugin is vetted and installed; after that its
matchers — bundled, local, or remote — carry no further risk, because a
matcher cannot do what the plugin can. Coded plugins and sandboxed matchers
are complementary: the plugin is where trusted, complex, compiled logic
belongs; the matcher is where a shareable rule belongs — and the second is
where nearly all the work happens.

## Measurements

!!! note "These are ratios, not a benchmark suite"
    Figures come from a single developer-machine run and vary with hardware
    and JIT state. The *shape* — Distill several times faster than Groovy on
    almost every matcher, several times slower than hand Java — is stable
    across runs; the exact microseconds are not.

### Method

`DistillGroovyParityTest.fullSweepParityAndPerformance` runs every
dual-implemented matcher (38 of them) against **five fixture specs**, for
**every scope the matcher declares**, supplying a loader-valid value for each
required parameter — 300 matcher × scope × spec combinations. For each it
asserts Groovy and Distill produce **identical diagnostics**, then times both:
best of five runs of 100 calls, after 50 warm-up calls. Groovy is compiled
once and the closure reused; Distill uses its parse cache — both as the
deployed engine would behave (or would have).

### Headline

| | Groovy (compiled once) | Distill (cached parse) |
|---|--:|--:|
| per-call time, summed over all 38 matchers | ~280 µs | **~65 µs** |
| mean speedup | — | **~4–5× faster** |
| full sweep, 300 combos | — | 300 identical, 0 divergent |

Across the wider loop, a full-policy pass (109 rules) is ~4.5 ms and an
end-to-end `validate()` on a 40-path spec is ~9.7 ms — of which the OpenAPI
parse (~3 ms) and model adaptation (~1 ms) are the larger share.

### Per-matcher

Microseconds per call, averaged over the matcher's scope × spec combinations.
Speedup is Groovy ÷ Distill.

| Matcher | Combos | Findings | Groovy µs/call | Distill µs/call | Speedup |
|---|--:|--:|--:|--:|--:|
| `api-title` | 5 | 0 | 5.5 | 1.3 | 4.3× |
| `bulk-operation` | 5 | 0 | 7.4 | 0.4 | 17.2× |
| `common-field` | 5 | 2 | 3.8 | 0.6 | 6.5× |
| `compatibility` | 20 | 0 | <0.1 | 0.1 | ~1× |
| `date-time-name` | 5 | 1 | 3.9 | 0.5 | 7.3× |
| `document-lint` | 5 | 0 | 1.1 | 0.2 | 4.6× |
| `documentation-completeness` | 10 | 4 | 7.4 | 1.3 | 5.7× |
| `enum-values` | 5 | 0 | 3.7 | 0.3 | 11.9× |
| `example-validity` | 10 | 2 | 2.0 | 0.5 | 3.8× |
| `extensions` | 5 | 2 | 25.5 | 1.4 | 18.1× |
| `header-schema` | 5 | 2 | 8.4 | 0.5 | 15.6× |
| `hostname` | 5 | 5 | 2.4 | 1.7 | 1.4× |
| `manual` | 20 | 0 | <0.1 | 0.1 | ~1× |
| `media-type` | 5 | 5 | 11.6 | 1.3 | 9.0× |
| `metadata` | 5 | 5 | 7.7 | 2.6 | 3.0× |
| `naming` | 30 | 13 | 6.8 | 1.4 | 5.0× |
| `openapi-version` | 5 | 5 | 4.3 | 2.1 | 2.1× |
| `operation` | 5 | 5 | 12.0 | 2.7 | 4.4× |
| `operation-metadata` | 10 | 10 | 9.8 | 1.6 | 6.0× |
| `operation-semantics` | 10 | 0 | 12.1 | 0.9 | 13.8× |
| `parameter` | 10 | 0 | 5.3 | 1.7 | 3.1× |
| `path-count` | 5 | 2 | 10.6 | 4.3 | 2.5× |
| `path-set` | 5 | 0 | 7.4 | 10.6 | 0.7× |
| `proprietary-header` | 5 | 2 | 18.7 | 1.6 | 11.6× |
| `query-collection` | 5 | 1 | 8.7 | 0.7 | 13.2× |
| `request-body` | 5 | 0 | 7.0 | 0.6 | 11.1× |
| `resource-path` | 10 | 0 | 7.9 | 3.0 | 2.7× |
| `response-code` | 10 | 0 | 7.1 | 0.9 | 7.8× |
| `response-example` | 5 | 1 | 14.1 | 1.5 | 9.2× |
| `response-header` | 5 | 0 | 7.9 | 0.6 | 13.8× |
| `schema` | 5 | 2 | 10.4 | 3.6 | 2.9× |
| `schema-composition` | 10 | 2 | 1.6 | 0.3 | 6.4× |
| `schema-name` | 5 | 2 | 3.7 | 1.1 | 3.3× |
| `security` | 5 | 5 | 11.3 | 5.5 | 2.0× |
| `server-url` | 5 | 1 | 4.0 | 1.4 | 2.9× |
| `status-class` | 5 | 2 | 10.2 | 1.6 | 6.5× |
| `text-style` | 5 | 2 | 9.0 | 1.6 | 5.5× |
| `versioning` | 20 | 0 | 1.6 | 0.4 | 3.6× |

**Reading the outliers**

- **`path-set` (0.7×)** is the only matcher where Distill is slower. Its DSL is
  `O(n²)` — a nested `api.paths.find { … }` inside both `filter` and `map`,
  re-tokenising every path — so Groovy's JIT-compiled inner loop edges ahead of
  the interpreted tree-walk. A matcher-authoring cost, not an engine cost:
  both do the same quadratic work.
- **`hostname`, `security`, `openapi-version` (~1.4–2.1×)** spend most of their
  time in RE2/J regex and string operations, shared by both engines, so the
  interpreter overhead is proportionally small.
- **`compatibility`, `manual` (~1×)** are sub-microsecond on both; the ratio is
  noise.
- **`extensions`, `proprietary-header`, `bulk-operation`, `header-schema`
  (12–18×)** spend their time in Groovy's dynamic dispatch and closure
  machinery over small collections — exactly what Distill's fixed builtin set
  and direct iteration avoid.

### The hand-written Java baseline

Five matchers spanning the cost spectrum were re-implemented as plain Java
(`JavaMatchers`, test scope only) against the same `(api, rule)` map contract.
All three implementations produce identical diagnostics; times are best of
five runs of 200, averaged over the fixture specs.

| Matcher | Findings | Groovy µs | Distill µs | Java µs | Distill ÷ Java | Groovy ÷ Java |
|---|--:|--:|--:|--:|--:|--:|
| `hostname` | 5 | 3.1 | 1.7 | 0.26 | 6.5× | 11.7× |
| `date-time-name` | 3 | 4.7 | 1.0 | 0.15 | 6.4× | 30.9× |
| `status-class` | 2 | 8.3 | 1.2 | 0.26 | 4.5× | 32.3× |
| `operation-semantics` | 2 | 20.7 | 6.5 | 1.23 | 5.2× | 16.8× |
| `path-set` (O(n²)) | 1 | 10.5 | 11.0 | 2.10 | 5.2× | 5.0× |

Hand-written Java is a stable **~5–6× faster than Distill** across trivial and
complex matchers alike — the price of a tree-walk over compiled bytecode — and
**5–32× faster than compiled Groovy**. Even on `path-set`, where Distill trails
Groovy, hand Java running the same algorithm beats both by 5×.

## What the overhead actually costs

At 1–11 µs per matcher call and ~150 evaluations per validation, the entire
matcher phase is **1–4 ms** — against a ~3 ms OpenAPI parse Areté cannot
avoid. Distill's interpreter overhead is real and measurable, and it is not
where a validation spends its time.

That is the trade in full: a few milliseconds per spec to keep every matcher a
~5–15-line sandboxed expression that can be edited without a rebuild and run
from any source — instead of ~40 lines of null-checked Java per matcher, in a
language where a rule change means a release and a matcher can never leave the
jar.

## Reproducing

```bash
mvn -pl policy-based-validation-plugin test \
  -Dtest='DistillGroovyParityTest#fullSweepParityAndPerformance+javaBaselineComparison' \
  -Darete.benchmark=true
```

The parity assertions run unconditionally in the normal build; the
`-Darete.benchmark=true` flag adds the timing tables.
`PerformanceGainBenchmarkTest` (same flag) covers the end-to-end and
allocation figures.
