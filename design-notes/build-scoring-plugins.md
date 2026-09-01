# Areté Scoring — Maven & Gradle build-gate plugins

> **Proposal — for review. Nothing here is implemented.**
>
> Revised: there is **no new SPI**. The plugins are thin clients of Areté's
> existing [Automation API](../docs/automation-api.md). Scoring stays entirely
> server-side; the build plugin only submits the spec, reads the verdict, and
> fails the build.

## Goal

A build-time quality gate. During `mvn verify` / `gradle check`, submit the
module's OpenAPI spec to an Areté instance, run it against one or more
**validator/policy combinations**, and **fail the build if any non-optional
combination fails its policy**.

## Core principle

Each **combination** (`<validator>/<policy>`, e.g. `generic-policy/Enterprise
Grade`) owns its own scale, passing score, and pass/fail logic — all on the
Areté server. The build plugin does **not**:

- normalise or merge scores across combinations,
- weight anything,
- compute pass/fail itself.

It takes each combination's server-computed verdict and combines them with a
logical **AND**, excluding any combination the build declares `optional`.

## Why no SPI

The original sketch had a `Scorer` SPI with local implementations. Dropped:
Areté already exposes exactly this as a network API, already supports multiple
independent validator plugins (`generic-policy`, and future ones like a
breaking-changes checker) each with their own verdict, and already computes
score/grade/passing-score/verdict per combination. A local SPI would duplicate
all of that. The build plugin's job shrinks to: **POST the spec, parse
`results[]`, apply the `optional` flags, fail or pass.**

## The Areté endpoint the plugins call

Submit-and-score in one request (from
[`docs/automation-api.md`](../docs/automation-api.md)):

```
POST {areteUrl}/api/v1/namespaces/{namespace}/specs
       ?run=<validator>/<policy>            # repeatable
       &failOn=policy                        # per-call default gate
     Cookie: arete_submitter=<submitter>
     Content-Type: application/yaml
     <spec body>

200/201  { "spec": {...}, "ok": true|false, "verdict": "PASS"|"FAIL",
           "results": [
             { "validator": "generic-policy", "policy": "Enterprise Grade",
               "status": "SUCCESS", "score": 93.5, "grade": "B+",
               "passingScore": 90.0,
               "level": { "criterion": "score<90", "source": "policy", "met": true },
               "counts": { "ERROR": 0, "WARNING": 20, ... } },
             ...
           ] }
```

Key facts the plugins rely on:

- The default response is **HTTP 200/201 with the verdict in the body** —
  `?httpStatusOnFail=422` is opt-in. The plugin reads `results[].level.met`
  (and `ok` / `verdict`), **not** the HTTP status, so it stays in control of
  per-combination `optional` handling.
- `namespace` and `submitter` are self-asserted labels, not credentials.
- `?format=sarif` is available if the plugin also wants to emit a SARIF file
  for CI code-scanning upload.

## Configuration

Both plugins share the same shape. Common case needs only the URL, namespace,
a spec path, and one `run`:

### Maven — `arete-maven-plugin`, goal `check`, phase `verify`

```xml
<plugin>
  <groupId>net.dublinx.arete</groupId>
  <artifactId>arete-maven-plugin</artifactId>
  <configuration>
    <areteUrl>${arete.url}</areteUrl>          <!-- from a profile, see below -->
    <namespace>${project.groupId}</namespace>
    <submitter>${arete.submitter}</submitter>  <!-- default: "maven" -->
    <spec>src/main/resources/openapi.yaml</spec>
    <combinations>
      <combination>
        <run>generic-policy/Enterprise Grade</run>
        <!-- optional omitted => gating -->
        <failOn>policy</failOn>                <!-- default: policy -->
      </combination>
      <combination>
        <run>generic-policy/Zalando</run>
        <optional>true</optional>
      </combination>
    </combinations>
  </configuration>
</plugin>
```

- No scorer dependencies, no `ServiceLoader`, no classpath work — it is an
  HTTP call.
- A failing `verdict` for any **non-optional** combination → **`MojoFailureException`**
  (a normal build failure).
- If Areté is unreachable → `MojoExecutionException` by default, or a warning
  when `<failOnUnavailable>false</failOnUnavailable>` (see open questions).
- Report to the log **and** `${project.build.directory}/arete-scoring/report.txt`
  (+ `report.json`, + `arete.sarif` when `<sarif>true</sarif>`).

### Gradle — plugin id `net.dublinx.arete`

```kotlin
areteScoring {
    url = providers.gradleProperty("arete.url").orElse("http://localhost:6809")
    namespace = project.group.toString()
    submitter = "gradle"
    spec = layout.projectDirectory.file("src/main/resources/openapi.yaml")

    combination("generic-policy/Enterprise Grade")           // gating
    combination("generic-policy/Zalando") { optional = true }
}
```

- Registers `areteScoringCheck`, wires `check.dependsOn(areteScoringCheck)`.
- Failing non-optional verdict → task throws through Gradle's normal
  verification-failure path (`VerificationException`).
- Report to `${layout.buildDirectory}/reports/arete-scoring/report.txt`
  (+ `.json`, + SARIF).
- `build.gradle` and `build.gradle.kts` both supported.

## Profiles — local Areté vs shared/CI Areté

The **URL is the only thing that differs** between "developer runs the build on
their laptop" and "CI runs the build against the team's Areté". Everything else
(namespace, combinations) stays the same.

### Maven

Two profiles in the module (or the parent) POM; the plugin config reads
`${arete.url}`:

```xml
<profiles>
  <profile>
    <id>arete-local</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <properties>
      <arete.url>http://localhost:6809</arete.url>
    </properties>
  </profile>
  <profile>
    <id>arete-ci</id>
    <activation><property><name>env.CI</name></property></activation>
    <properties>
      <arete.url>https://arete.internal.example.com</arete.url>
    </properties>
  </profile>
</profiles>
```

- Local build: nothing to pass — `arete-local` is active by default, points at
  `localhost:6809` (a developer's locally-installed Areté).
- CI: the `CI` env var (set by every major CI system) activates `arete-ci`.
- Either can be forced with `-Parete-ci` / `-Parete-local`, or the URL
  overridden ad hoc with `-Darete.url=…`.

### Gradle

`arete.url` is a Gradle property with a `localhost:6809` default in the plugin.
Override per environment:

- `gradle.properties` in the project → committed default (usually left as
  localhost).
- `~/.gradle/gradle.properties` → a developer's machine-wide override.
- CI → `-Parete.url=https://arete.internal.example.com` or
  `ORG_GRADLE_PROJECT_arete.url` env var.

(Gradle has no first-class "profiles"; property layering is the idiomatic
equivalent and the plugin documents the three override points.)

## Report format (identical across both build tools)

Lives in the shared core module so Maven and Gradle logs read the same:

```
Areté Scoring — module: my-service   (arete: http://localhost:6809)

  COMBINATION                        SCORE   GRADE  GATE          RESULT   GATING
  generic-policy/Enterprise Grade    93.5    B+     score<90      PASS     yes
  generic-policy/Zalando             91.0    A-     error         PASS     no (optional)

  Overall: PASS
```

On failure the row shows `FAIL` and the `Overall` line names which non-optional
combination(s) failed. Optional combinations always appear, labelled
non-gating, so nothing is hidden.

## Modules

Now three, not four (no SPI):

| Module | Purpose |
|---|---|
| `arete-build-scoring-core` | HTTP client for the Automation API + the report formatter. No Maven/Gradle types. Unit-testable against a stub server. |
| `arete-maven-plugin` | `check` goal → `verify`, delegates to core. |
| `arete-gradle-plugin` | `areteScoringCheck` task → `check`, delegates to core. |

## Non-goals

- A local `Scorer` SPI or any local scoring logic.
- A merged/normalised score across combinations.
- Weighting, quorum, merge modes.
- Managing an Areté instance (starting/stopping a local server) — the plugin
  assumes one is reachable at the configured URL.
- Aggregating verdicts across reactor modules — each module gates itself.

## Open questions for review

1. **`net.dublinx.arete`** vs the existing `net.dublinux.arete` (with a `u`) —
   deliberate new groupId or a typo?
2. **Name** — `arete-scoring-spi` already exists (OpenAPI policy scoring
   library). This proposal uses `arete-build-scoring-core` + `arete-maven-plugin`
   / `arete-gradle-plugin`. OK, or rename to "Areté Gate"?
3. **Unreachable Areté** — hard-fail the build, or warn-and-skip? Proposed:
   hard-fail by default (a silent skip defeats the gate), overridable.
4. **Spec discovery** — a single `<spec>` path, a glob, or auto-detect
   (`src/main/resources/**/openapi.{yaml,json}`)? Multi-spec modules?
5. **Namespace default** — `${project.groupId}` / `project.group`, or require
   it explicitly? Does CI want a per-branch namespace?
6. **Submitter default** — `"maven"` / `"gradle"`, or derive from CI env
   (`GITHUB_ACTOR`, `BUILD_USER`, …)?
7. **`failOn` per combination vs one global** — the API takes one `failOn` per
   call; N combinations with different `failOn` values need N calls (or the
   plugin computes the verdict from `level.met` and ignores `failOn`). Prefer
   the latter — one call, plugin owns the AND.
8. **SARIF** — emit by default, or opt-in?
9. **Caching** — the API keys results by spec content hash; should the plugin
   short-circuit when the spec hasn't changed since the last build?
10. **Repo** — new modules here, or a separate repo (Gradle plugin publishing
    vs the Maven reactor)?

## Suggested build order (checkpoint each with review)

1. `arete-build-scoring-core` — API client + report formatter, tested against a
   stub HTTP server. No build-tool code.
2. Maven plugin — `check` goal, tested against a sample `pom.xml` and a real
   local Areté; the `arete-local` / `arete-ci` profile pattern.
3. Gradle plugin — same, `build.gradle` **and** `build.gradle.kts`, the
   property-layering pattern.
4. Failure-path tests, both tools: failing gating combination; failing optional
   combination (build still passes); unreachable Areté; unknown
   validator/policy (the API returns a 4xx / an error result — surface it
   clearly).
