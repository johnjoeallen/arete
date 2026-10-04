# Areté Serverless Core: Implementation Status

2026-10-04

Companion to [serverless-core-plan.md](serverless-core-plan.md). The plan says what to build and why; this
note tracks the stages, what each needs, and how far along each is. Work lands on `merge/serverless-core`
and reaches `main` by merge.

Percentages are a judgement of delivered scope against the plan's scope for that stage, not a measure of
lines or hours. A stage is 100% only when everything the plan lists for it is built, tested and exercised.

## Summary

| Stage | Goal | Complete |
| --- | --- | --- |
| 0 | Fix false positives and deep `$ref` | 90% |
| 1 | Zally parity corpus | 50% |
| 2 | Extract the engine, policy loader, CLI, reports | 95% |
| 3 | Merge-gate and thin build plugins | 95% |
| 4 | Host policy in-org, report-only trial, enforce | 15% |
| Extras | Extended rule semantics, Distill values, publishing | 70% |

Overall: roughly 75%. The software is largely built. What is left is mostly proof against real material,
the first publish, and the org rollout, which depend on things outside the repository.

## Stage 0: fix false positives (90%)

| Item | State |
| --- | --- |
| Content type defined through `$ref`, and other false positives | Done, with regression tests |
| `$ref` resolved transparently; nested schema properties exposed | Done |
| Rule corpus: bad and good pair for every bundled rule | Done |
| Whole-API corpus: good and messy specs | Done |
| Cycle detection, depth limit, ref-to-ref chains | Done for the model; JSON025 measures nesting depth |
| "Reached via" path on a shared-schema finding | Not done |
| External (relative file and URL) refs resolved against the base commit in the gate | Partly: head resolves; base resolution against the merge-base for multi-file specs needs a trial |
| Known gaps recorded in the corpus | JSON013 is now fixed; none outstanding |

Remaining: "reached via" reporting and a multi-file spec trial through the gate.

## Stage 1: Zally parity corpus (50%)

| Item | State |
| --- | --- |
| Rule-pair corpus as a golden regression suite in the repo | Done |
| Whole-API good and messy specs | Done |
| Rule conflicts between policies settled | Done; cross-policy conflicts are intentional |
| Zally rule ID to Areté rule mapping | Not done |
| Run of the Global Platform specs through Areté and Zally | Not done: those specs and the Zally scripts are not reachable from this environment |
| Differences classified as over-reporting, under-reporting or intentional | Not done |

The synthetic corpus gives regression protection but not parity evidence. This stage needs someone with
access to the organisation's specs, or a public spec set agreed as a stand-in.

## Stage 2: extract the engine (95%)

| Item | State |
| --- | --- |
| `arete-engine-api` and `arete-engine` split; plain `Engine` class | Done |
| Groovy and the scoring-plugin SPI removed | Done |
| App embeds the engine; leftover "Plugin" names renamed, including stored table and column | Done |
| Policy loader: file, https, Maven, git sources; pin, checksum, cache | Done |
| Layered sources, `locked` rules, `.arete.yaml` overrides | Done |
| Maven `settings.xml` credentials for `maven:` sources | Done |
| `arete` CLI: `score`, `diff`, `report`, `policy verify` | Done |
| JSON, markdown and SARIF writers, with line numbers | Done |
| Extended rule semantics (`format: 2`: `expect: match`, `per-match`, `max`, `tiers`) | Done |
| Signature checking of fetched bundles | Not started; the plan treats it as optional and later |
| CLI fat-jar size measured after Groovy removal | Not recorded |

## Stage 3: merge-gate (95%)

| Item | State |
| --- | --- |
| Stable pointers (parameters by `in:name`, responses by status, tags by name) | Done |
| Pointer to line and column mapping | Done |
| Occurrence matching and NEW, EXISTING, RESOLVED classification | Done |
| `arete gate`: git base or raw base, reports, `--report-only` | Done |
| Gate rules, including scoring every spec and judging unchanged ones alone | Done |
| Count-based and tiered rules compared by count in the gate | Done |
| Maven plugin (`arete:gate`, `arete:score`) over a shared `GateJob` | Done |
| Gradle plugin (`areteGate`, `areteScore`) over the same job | Done |
| Tried from a local install on a real git repository: regression, new and moved specs, layered `git:` policy, shallow clone | Done |
| SARIF `physicalLocation` for inline MR annotations | Done in the writer; not yet seen rendering in GitLab or GitHub |
| `--annotate-changed-only` | Not done (optional) |
| Acceptance against MR 1109 and the org's Zally scripts | Not done: not reachable from here |

## Stage 4: host policy in-org (15%)

| Item | State |
| --- | --- |
| Engine, API and both plugins publishable from `publish.yml` | Done; the workflow has not been run |
| Old `arete-ci-gate-*` modules retired | Done |
| First real publish (Maven Central or internal repository) | Not done |
| Policy bundle published to an internal repository with version and checksum | Not done |
| Pipeline job in `--report-only` | Not done |
| Job made required | Not done |
| Deprecated `automation-api` and HTTP-client gate removed after one release | Not done; `AutomationApiController` remains |

This stage is mostly the organisation's work. The repository side is ready once the publish workflow has
been run once.

## Extras beyond the stage list (70%)

| Item | State |
| --- | --- |
| Distill values: `occurrence(..., value)` with `measure: value` tiers; gate compares the worst value | Done |
| JSON025 nesting depth; STATUS001, STANDARD011 tiered | Done |
| Distill combinators and whole-spec scope | Deferred (see `distill-extensions.md`) |
| Resource-path segment sub-delimiters; nullable haystacks in operation semantics | Deferred |
| Rule documentation links use the application's own URL | Done |
| Scoring panel picks the policy only | Done |

## What is left, in order

1. Run `publish.yml` once and confirm the engine, API and both plugins resolve from the published location.
2. Obtain a real spec set, then do Stage 1: map Zally IDs, run both tools, classify the differences, and
   record the golden results.
3. Try the gate on a multi-file spec with external refs, and fix base-side resolution if needed.
4. See a SARIF file render inline in GitLab and in GitHub.
5. Add "reached via" paths to findings on shared schemas.
6. Publish the policy bundle internally and start a `--report-only` job; make it required once the noise is
   understood.
7. Remove the deprecated automation API one release after the gate is in use.
8. Optional: signature checking, `--annotate-changed-only`, Distill combinators.
