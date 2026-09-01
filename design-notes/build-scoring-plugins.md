# Areté Scoring — Maven & Gradle build-gate plugins

> **Proposal — for review. Nothing here is implemented.**
>
> The plugins are thin clients of Areté's existing
> [Automation API](../docs/automation-api.md). Scoring stays entirely
> server-side; a plugin only submits the spec, reads the verdict, and fails the
> build.

## Goal

A build-time quality gate. During `mvn verify` / `gradle check`, submit the
module's OpenAPI spec to an Areté instance, run it against one or more
**validator/policy combinations**, and **fail the build if any non-optional
combination fails its policy**.

## Core principle

Each **combination** (`<validator>/<policy>`, e.g. `generic-policy/Enterprise
Grade`) owns its own scale, passing score, and pass/fail logic — all on the
Areté server, which already supports multiple independent validator plugins
(`generic-policy`, and future ones like a breaking-changes checker) each with
their own verdict. The build plugin does **not**:

- normalise or merge scores across combinations,
- weight anything,
- compute pass/fail itself.

Its whole job is: **POST the spec, read each combination's server-computed
verdict, combine them with a logical AND** — excluding any combination the
build declares `optional`.

## The Areté endpoint the plugins call

Submit-and-score in one request (from
[`docs/automation-api.md`](../docs/automation-api.md)). The plugin **always**
sends `httpStatusOnFail=422` so the outcome is on the HTTP status line, and —
when a SARIF file is wanted — `format=sarif` in the *same* call:

```
POST {areteUrl}/api/v1/namespaces/{namespace}/specs
       ?run=<validator>/<policy>            # repeatable
       &httpStatusOnFail=422                 # always
       &format=sarif                         # only if a SARIF file is wanted
     Cookie: arete_submitter=<submitter>
     Content-Type: application/yaml
     <spec body>

201  (all gating combinations passed)   body: SubmitResponse  (or SARIF)
422  (a combination failed its policy)  body: SubmitResponse  (or SARIF)
4xx/5xx other                           body: Problem Details {status,title,detail}
```

`SubmitResponse` (JSON form):

```json
{ "spec": {...}, "ok": true|false, "verdict": "PASS"|"FAIL",
  "results": [
    { "validator": "generic-policy", "policy": "Enterprise Grade",
      "status": "SUCCESS", "score": 93.5, "grade": "B+", "passingScore": 90.0,
      "level": { "criterion": "score<90", "source": "policy", "met": true },
      "counts": { "ERROR": 0, "WARNING": 20 } },
    ...
  ] }
```

How the plugin reads it:

- **`201` → build passes.** No combination failed. Save the SARIF if requested;
  print the report from `results[]`.
- **`422` with a `results[]` body → a scoring failure.** Parse `results[]`,
  build the report, and compute the **build** verdict as
  *AND of `level.met` over the non-`optional` combinations* — so a `422`
  caused only by an `optional` combination is downgraded to a **build pass**
  (still shown in the report, marked non-gating). SARIF, if requested, is
  saved regardless.
- **Any other status, or `422` without `results[]` (Problem Details body) →
  build error** — unreachable Areté, unknown validator/policy, bad spec.
  Surface `title` / `detail`; do not treat as a scoring failure.
- The policy already owns pass/fail (`passingScore`, or `scoring: blocker |
  error`); `level.met` reflects that. The plugin passes **no `failOn`** in the
  normal case — see below.
- `namespace` and `submitter` are self-asserted labels, not credentials.

## Configuration

Both plugins share the same shape. Common case needs only the URL, namespace,
a spec path, and one `run`:

### Maven — `arete-maven-plugin`, goal `check`, phase `verify`

```xml
<plugin>
  <groupId>net.dublinux.arete</groupId>
  <artifactId>arete-maven-plugin</artifactId>
  <configuration>
    <areteUrl>${arete.url}</areteUrl>          <!-- from a profile, see below -->
    <namespace>${project.groupId}</namespace>
    <submitter>${arete.submitter}</submitter>  <!-- default: "maven" -->
    <spec>src/main/resources/openapi.yaml</spec>
    <combinations>
      <combination>
        <run>generic-policy/Enterprise Grade</run>   <!-- gating; policy owns pass/fail -->
      </combination>
      <combination>
        <run>generic-policy/Zalando</run>
        <optional>true</optional>                    <!-- runs, reported, excluded from the gate -->
      </combination>
    </combinations>
  </configuration>
</plugin>
```

- No plugin dependencies to declare, no classpath work — it is one HTTP call,
  always with `httpStatusOnFail=422`.
- `422` for a **non-optional** combination → **`MojoFailureException`** (a
  normal build failure); `201`, or `422` from only `optional` combinations →
  build passes.
- Non-scoring failure (unreachable, `4xx` other than the scoring `422`,
  unknown validator/policy) → **`MojoExecutionException`** by default;
  `<failOnUnavailable>false</failOnUnavailable>` downgrades an unreachable
  Areté to a warning (see open questions).
- `<failOn>` is accepted per combination but omitted from the normal case —
  an advanced override, passed straight through to the API, only for holding a
  **stricter** bar than the policy (`error`, `blocker`, `score<NN`).
- Report to the log **and** `${project.build.directory}/arete-scoring/report.txt`
  (+ `report.json`, + `arete.sarif` when `<sarif>true</sarif>`).

### Gradle — plugin id `net.dublinux.arete`

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

Three:

| Module | Purpose |
|---|---|
| `arete-build-scoring-core` | HTTP client for the Automation API + the report formatter. No Maven/Gradle types. Unit-testable against a stub server. |
| `arete-maven-plugin` | `check` goal → `verify`, delegates to core. |
| `arete-gradle-plugin` | `areteScoringCheck` task → `check`, delegates to core. |

## Non-goals

- Any local scoring logic — all of it is server-side in Areté.
- A merged/normalised score across combinations.
- Weighting, quorum, merge modes.
- Managing an Areté instance (starting/stopping a local server) — the plugin
  assumes one is reachable at the configured URL.
- Aggregating verdicts across reactor modules — each module gates itself.

## Open questions for review

1. **Name** — the artifact names here (`arete-build-scoring-core`,
   `arete-maven-plugin`, `arete-gradle-plugin`) are provisional. "Areté Gate"
   is an alternative if "scoring" is too close to existing artifact names.
2. **Unreachable Areté** — hard-fail the build, or warn-and-skip? Proposed:
   hard-fail by default (a silent skip defeats the gate), overridable.
3. **Spec discovery** — a single `<spec>` path, a glob, or auto-detect
   (`src/main/resources/**/openapi.{yaml,json}`)? Multi-spec modules?
4. **Namespace default** — `${project.groupId}` / `project.group`, or require
   it explicitly? Does CI want a per-branch namespace?
5. **Submitter default** — `"maven"` / `"gradle"`, or derive from CI env
   (`GITHUB_ACTOR`, `BUILD_USER`, …)?
6. **Per-combination `failOn` overrides** — the API takes one `failOn` per
   call, so N combinations each overriding it would need N calls. The normal
   case sends none (policy owns the gate) and does one call; only a
   `<failOn>`-carrying combination forces a second call. Acceptable, or drop
   per-combination `failOn` entirely and support one build-wide override?
7. **`422` overloading** — Areté returns `422` for both "a combination failed
   its policy" and "unknown validator / bad request". The plugin distinguishes
   by body shape (`results[]` present vs Problem Details). Fine, or should the
   API use a distinct status for the scoring-failure case?
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
