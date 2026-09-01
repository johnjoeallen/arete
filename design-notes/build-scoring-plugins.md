# Areté Scoring — Maven & Gradle build-gate plugins

> **Proposal — for review. Nothing here is implemented.** This is a new
> subsystem, separate from the existing web app and its OpenAPI policy engine.

## Goal

A build-time quality gate. During `mvn verify` / `gradle check`, run one or
more **independent scoring plugins** against the module and **fail the build
if any non-optional scorer fails its own policy**.

## Core principle

Each scoring plugin owns **its own scale and its own pass/fail threshold**.
There is:

- no cross-plugin normalization,
- no weighting,
- no merged numeric score,
- no merge "mode" (quorum, weighted average, …).

The orchestrator runs every configured scorer and combines their individual
**verdicts** with a logical **AND**, excluding any scorer marked `optional`
from that AND. It **never** derives pass/fail from a score and a threshold
itself — some scorers have non-monotonic or multi-condition pass logic, so the
verdict the scorer returns is authoritative.

## Modules

Four modules, built and checkpointed in this order.

### 1. SPI — `arete-build-scoring-spi`

The contract every scorer implements. Small, dependency-free, published to
Maven Central, versioned additively (new optional fields via builders, never a
breaking constructor change — same discipline as `arete-scoring-spi`).

```
interface Scorer {
    String id();                       // stable, e.g. "openapi", "coverage", "mutation"
    ScoreResult score(ScoringContext context);
}

ScoringContext:
    Path   projectDir            // the module being scored
    Path   buildOutputDir        // target/ or build/
    Map<String,Object> config    // this scorer's opaque config block, or empty

ScoreResult (immutable, builder):
    double  score                // the scorer's own raw number
    double  threshold            // the threshold the scorer actually used
    boolean passed               // the scorer's own verdict — trusted verbatim
    Map<String,Object> details   // free-form diagnostics, may be empty
```

Notes for review:

- `config` and `details` as `Map<String,Object>` keeps the SPI free of a
  config-schema dependency and lets the report file serialise `details` as
  JSON. Alternative: `details` as a plain `String`.
- The **configured** threshold is passed in via `config` (or a dedicated
  `ScoringContext.threshold()` — see open questions); the scorer echoes back
  in `ScoreResult.threshold` whatever it actually applied, which need not
  match.
- Java 17 baseline (matches the rest of Areté).

### 2. Orchestrator / core — `arete-build-scoring-core`

Build-tool-agnostic. Both plugins delegate here so behaviour and report
formatting are identical. No Maven or Gradle types on its classpath; unit
testable on its own.

```
record ScorerRequest(String id, double threshold, boolean optional,
                     Map<String,Object> config)

record ScorerOutcome(String id, boolean optional, ScoreResult result,
                     Throwable error)      // error != null => treated as a fail

record OverallResult(List<ScorerOutcome> outcomes, boolean passed, String report)

class Orchestrator {
    // scorers already resolved & instantiated by the caller (the plugin)
    OverallResult run(List<ScorerRequest> requests, Map<String,Scorer> scorers);
}
```

Behaviour:

1. Run every request's scorer, **sequentially, in declared order**, each with a
   `ScoringContext` built from the module dir + build output dir + its config
   block. A thrown exception becomes a failing outcome (never aborts the run).
2. `passed = every non-optional outcome passed`.
3. Produce the report — see below.

The core does **not** do dependency resolution or classpath work. The plugin
hands it a `Map<id → Scorer>` it already resolved.

### Report format (identical across both build tools)

Plain text, deterministic, one row per scorer **including optional ones**,
optional rows clearly marked non-gating. Sketch:

```
Areté Scoring — module: my-service

  SCORER      SCORE    THRESHOLD   RESULT   GATING
  openapi     93.5     90.0        PASS     yes
  coverage    71.2     80.0        FAIL     yes
  mutation    64.0     60.0        PASS     no (optional)

  Overall: FAIL  (coverage did not pass)
```

Also written as `report.json` alongside for CI tooling (open question: keep or
drop the JSON). The exact column widths/wording are fixed in `core` so Maven
and Gradle logs read identically.

### 3. Reference scorer — `arete-build-scoring-scorer-noop`

A trivial `Scorer` that always passes (`score = threshold, passed = true`,
empty details). Exists only to prove the SPI + core + both plugins end to end
before any real scorer is written. Ships in `src/test` fixtures, not published.

### 4a. Maven plugin — `arete-maven-plugin`

- Goal **`check`**, bound by default to **`verify`**.
- Scorer implementations are declared as **plugin-level `<dependencies>`**.
- Config:

  ```xml
  <plugin>
    <groupId>net.dublinx.arete</groupId>
    <artifactId>arete-maven-plugin</artifactId>
    <configuration>
      <scorers>
        <scorer>
          <id>openapi</id>
          <threshold>90</threshold>
          <!-- optional omitted => gating -->
          <config>
            <policy>Enterprise Grade</policy>
            <spec>src/main/resources/openapi.yaml</spec>
          </config>
        </scorer>
        <scorer>
          <id>coverage</id>
          <threshold>80</threshold>
        </scorer>
        <scorer>
          <id>mutation</id>
          <threshold>60</threshold>
          <optional>true</optional>
        </scorer>
      </scorers>
    </configuration>
    <dependencies>
      <dependency>
        <groupId>net.dublinx.arete</groupId>
        <artifactId>arete-openapi-scorer</artifactId>
        <version>…</version>
      </dependency>
      <!-- … -->
    </dependencies>
  </plugin>
  ```

- **Resolution at execute time**: `ServiceLoader<Scorer>` over the Mojo's own
  plugin class realm (which already contains the declared `<dependencies>` —
  this is normal Maven plugin dependency resolution, **not** folder scanning).
  Every declared `<id>` must map to exactly one loaded `Scorer`; a declared id
  with no implementation → **`MojoExecutionException`** (fail fast, clear
  message naming the id and the ids that *are* available).
- A failing `OverallResult` → **`MojoFailureException`** (a normal build
  failure, not an internal plugin error).
- Report written to the log **and** to
  `${project.build.directory}/arete-scoring/report.txt` (+ `.json`).
- Per-module: in a reactor, each module runs `check` against its own dir.

### 4b. Gradle plugin — id `net.dublinx.arete`

- Registers a task (e.g. `areteScoringCheck`) and wires `check.dependsOn` it.
- Extension mirroring the Maven shape:

  ```kotlin
  areteScoring {
      scorer("openapi") {
          threshold = 90.0
          config = mapOf(
              "policy" to "Enterprise Grade",
              "spec" to "src/main/resources/openapi.yaml",
          )
      }
      scorer("coverage") { threshold = 80.0 }          // gating by default
      scorer("mutation") { threshold = 60.0; optional = true }
  }

  dependencies {
      areteScorer("net.dublinx.arete:arete-openapi-scorer:…")
  }
  ```

- A **dedicated dependency configuration** `areteScorer`, separate from
  `compileClasspath` / `runtimeClasspath`, holds the scorer implementation
  artifacts.
- Resolution: `ServiceLoader<Scorer>` over a classloader built from the
  resolved `areteScorer` configuration (standard practice for a Gradle plugin
  that needs isolated tool dependencies — again, not runtime discovery of
  arbitrary jars). Same fail-fast rule for an unmatched id.
- A failing `OverallResult` → the task throws through Gradle's normal
  verification-failure path (`VerificationException` / `GradleException`), so
  `--continue` and reporting behave as for any other `check` task.
- Report to `${layout.buildDirectory}/reports/arete-scoring/report.txt`
  (+ `.json`), same format string as Maven.
- Support both `build.gradle` and `build.gradle.kts`.

## Requirements applying to both plugins

- **No boilerplate for the common case** — a scorer needs only an `id` and a
  `threshold`; `optional` defaults to gating; `config` defaults to empty.
- **Identical report structure** between the two tools — the format lives in
  `core`, the plugins only choose where to write it.
- **Portable scorers** — the same scorer artifact works unmodified under either
  build tool, because both plugins call the same SPI + core.
- **No dynamic classloading / runtime plugin discovery** — scorers are ordinary
  build dependencies resolved by each build tool. `ServiceLoader` over an
  already-resolved dependency set is fine; scanning a folder for jars is not.
- **No merge modes** — the only combination is AND-of-non-optional-verdicts.

## Non-goals

- A merged/normalised score across scorers.
- Weighting, quorum, or any configurable combination strategy.
- Running scorers in a separate JVM / sandbox (they run in the plugin's
  process; a misbehaving scorer is the user's own declared dependency).
- Aggregating results across reactor modules into one verdict (each module
  gates itself).

## Relationship to the existing Areté

This reuses the **brand and the "scoring" vocabulary** but is a distinct
codebase from the web app. The most obvious first real scorer,
`arete-openapi-scorer`, wraps the existing policy engine
(`arete-policy-plugin` / the Automation API) so a project can gate its build on
its OpenAPI spec's policy score — but the framework itself knows nothing about
OpenAPI.

## Open questions for review

1. **`net.dublinx.arete`** — the existing project publishes under
   `net.dublinux.arete` (with a `u`). Is `net.dublinx.arete` a deliberate new
   groupId or a typo? All artifact names below assume it is intentional.
2. **Name collision** — `arete-scoring-spi` already exists for OpenAPI *policy*
   scoring. This proposal uses `arete-build-scoring-spi` / `-core` to
   disambiguate. Alternatives: a `net.dublinx.arete.build` package split, or
   renaming this to "Areté Gate".
3. **Repo** — new modules in this monorepo, or a separate repository? (Gradle
   plugin publishing and the Maven reactor don't mix cleanly.)
4. **`details` type** — `Map<String,Object>` (JSON-friendly) vs plain `String`.
5. **`report.json`** — worth maintaining, or is the text report enough?
6. **Threshold delivery** — as a well-known key inside `config`, or a
   first-class `ScoringContext.threshold()` accessor?
7. **Optional-scorer failure** — logged at `WARN`? Shown with a distinct
   marker in the report (done above) — anything else?
8. **Scorer inputs** — is `projectDir` + `buildOutputDir` enough, or do
   scorers need the resolved dependency classpath, the list of source roots,
   the module coordinates, etc.? Adding fields later is cheap; better to know
   now.
9. **Parallel execution** — sequential is proposed for determinism. Any need
   for parallel?
10. **Config typing on the Gradle side** — free-form `Map<String,Object>` vs a
    typed nested DSL per scorer (the latter needs each scorer to ship a Gradle
    extension type, which breaks "portable, build-tool-agnostic scorer").

## Suggested build order (checkpoint each with review before proceeding)

1. SPI module alone — no build-tool integration.
2. Orchestrator/core — unit-tested independently, including report formatting.
3. `noop` reference scorer — validate SPI + core end to end.
4. Maven plugin — tested against a sample multi-scorer `pom.xml`.
5. Gradle plugin — tested against sample `build.gradle` **and**
   `build.gradle.kts`.
6. Failure-path tests, both tools: unmatched scorer id; failing required
   scorer; failing optional scorer (build still passes, report still shows it).
