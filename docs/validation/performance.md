# Matcher performance: Distill vs Groovy

Areté evaluates every matcher with [Distill](distill.md). The `Matcher.groovy`
files that ship next to some `Matcher.dsl` files are a build-time parity
reference only — see the [policy engine](policy-engine.md) overview. This page
records how the two engines compare, so the choice of a purpose-built
interpreter over an embedded scripting language is grounded in numbers rather
than assertion.

## Method

`DistillGroovyParityTest.fullSweepParityAndPerformance` runs every
dual-implemented matcher (38 of them) against **five fixture specs**, for
**every scope the matcher declares**, supplying a loader-valid value for each
required parameter — 300 matcher × scope × spec combinations in total. For each
combination it:

- asserts Groovy and Distill produce **identical diagnostics**, then
- times both engines: best of five runs of 100 calls each, after 50 warm-up
  calls.

Two things are deliberately levelled so the comparison is about the engines,
not their wiring:

- **Groovy is compiled once** and the closure reused. The plugin's former
  Groovy path recompiled the script with a fresh `GroovyShell` on *every*
  call — roughly 50× slower again than the figures below — but that is an
  integration flaw, not an inherent property of Groovy.
- **Distill uses its parse cache**: the program is parsed once at bundle load
  and reused, which is exactly how the deployed engine behaves.

!!! note "These are ratios, not a benchmark suite"
    Figures come from a single developer-machine run and will vary with
    hardware and JIT state. The *shape* — Distill several times faster on
    almost every matcher, at a fraction of the allocation — is stable across
    runs; the exact microseconds are not.

## Headline

| | Groovy (compiled once) | Distill (cached parse) |
|---|--:|--:|
| per-call time, summed over all 38 matchers | ~280 µs | **~65 µs** |
| mean speedup | — | **~4–5× faster** |
| full sweep, 300 combos | — | 300 identical, 0 divergent |

Across the wider evaluation loop the same changes take a full-policy pass
(109 rules) from ~8.8 ms to ~4.5 ms and roughly a third of the allocation; an
end-to-end `validate()` on a 40-path spec drops from ~15.7 ms to ~9.7 ms, of
which the OpenAPI parse (~3 ms) and model adaptation (~1 ms) are now the larger
share.

## Per-matcher

Time is microseconds per call, averaged over the matcher's scope × spec
combinations. "Speedup" is Groovy ÷ Distill.

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

### Reading the outliers

- **`path-set` (0.7×)** is the only matcher where Distill is slower. Its DSL is
  `O(n²)` — a nested `api.paths.find { … }` inside both `filter` and `map`,
  re-tokenising every path each time — so the inner loop runs thousands of
  times on the fixture specs and Groovy's JIT-compiled bytecode edges ahead of
  the interpreted tree-walk. This is a matcher-authoring cost, not an engine
  cost: both engines do the same quadratic work.
- **`hostname`, `security`, `openapi-version` (~1.4–2.1×)** do most of their
  work in RE2/J regex and string operations, where the two engines share the
  same underlying calls and the interpreter overhead is proportionally small.
- **`compatibility`, `manual` (~1×)** are sub-microsecond on both engines with
  these inputs; the ratio is noise.
- **`extensions`, `proprietary-header`, `bulk-operation`, `header-schema`
  (12–18×)** spend their time in Groovy's dynamic dispatch and closure
  machinery over small collections — exactly the overhead Distill's fixed
  builtin set and direct iteration avoid.

## A hand-written Java baseline

To place both engines against the floor, five matchers spanning the cost
spectrum were re-implemented as plain Java (`JavaMatchers`, test scope only)
against the same `(api, rule)` map contract. All three implementations produce
identical diagnostics; the times are microseconds per call, best of five runs
of 200, averaged over the fixture specs.

| Matcher | Findings | Groovy µs | Distill µs | Java µs | Distill ÷ Java | Groovy ÷ Java |
|---|--:|--:|--:|--:|--:|--:|
| `hostname` | 5 | 3.1 | 1.7 | 0.26 | 6.5× | 11.7× |
| `date-time-name` | 3 | 4.7 | 1.0 | 0.15 | 6.4× | 30.9× |
| `status-class` | 2 | 8.3 | 1.2 | 0.26 | 4.5× | 32.3× |
| `operation-semantics` | 2 | 20.7 | 6.5 | 1.23 | 5.2× | 16.8× |
| `path-set` (O(n²)) | 1 | 10.5 | 11.0 | 2.10 | 5.2× | 5.0× |

Hand-written Java is **~5–6× faster than Distill**, consistently, across trivial
and complex matchers alike — that multiple is the price of a tree-walking
interpreter over compiled bytecode, and it is stable and predictable. It is
also **5–32× faster than compiled Groovy**. For `path-set`, where an O(n²)
algorithm dominates, Distill falls slightly behind Groovy's JIT-compiled inner
loop, but hand Java — running the same algorithm — is 5× faster than either.

That is the whole trade. A validation runs ~150 rule evaluations; at 1–11 µs
each the entire matcher phase is 1–4 ms, against a ~3 ms OpenAPI parse it
cannot avoid. Distill spends a few milliseconds of interpreter overhead per
spec to keep matchers as safe-by-construction declarative text — editable
without a rebuild, ~5–15 lines each, with no unsandboxed code path — rather
than ~40 lines of null-checked map navigation per matcher in a language where
a rule change means recompile and redeploy. The Groovy it replaced offered
none of Java's speed and none of Distill's safety.

!!! warning "The Java baseline is a measurement, not a proposal"
    Hand-written Java matchers — the model [Zally](https://github.com/zalando/zally)
    uses, where every rule is a compiled class — are **explicitly what Areté is
    built to avoid**, and the speed advantage above does not change that.

    A rule whose logic isn't expressible with an existing matcher would need a
    **new Java class, a new release, and a redeploy** before anyone could use
    it. Matchers could only ever ship inside the application jar.

    Areté's matchers are declarative text (`Matcher.dsl`) evaluated by a
    sandboxed interpreter precisely so that a new matcher is **data, not code**.
    User [policy files](../configuration.md) already load from outside the jar,
    and the same property is what makes the intended next step possible: a
    matcher **delivered from a remote source over the internet** can be run
    safely without trusting its author with the host JVM. Hand-written Java
    forecloses that entirely. A few milliseconds of interpreter overhead per
    spec is the price of keeping it open, and it is one worth paying.

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
