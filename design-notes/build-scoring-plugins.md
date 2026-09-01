# Areté CI Gate — Maven & Gradle build-gate plugins

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

201   spec was scored, every combination passed its policy   body: SubmitResponse (or SARIF)
422   spec was scored, at least one combination failed        body: SubmitResponse (or SARIF)
4xx   the request was rejected (bad spec, unknown validator/policy, bad namespace)
5xx   Areté error
```

**`422` means one thing: a scoring failure.** Request rejections use other
`4xx` codes (`400` / `404`), so the plugin never has to disambiguate a `422`.
*(Areté API change — see follow-up.)*

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

- **`201`** — the spec was scored, everything passed. Save the SARIF if
  requested; print the report from `results[]`.
- **`422`** — the spec was scored, something failed. Parse `results[]`, build
  the report, and compute the **build** verdict as *AND of `level.met` over
  the non-`optional` combinations* — so a `422` caused only by an `optional`
  combination is still a **build pass** (shown in the report, marked
  non-gating). Save the SARIF regardless.
- **Any other non-2xx** — the request was rejected or Areté errored. **Build
  error** (`MojoExecutionException` / `GradleException`), surfacing the
  Problem Details `title` / `detail`. Never treated as a scoring failure.
- The policy already owns pass/fail (`passingScore`, or `scoring: blocker |
  error`); `level.met` reflects that. The plugin passes **no `failOn`** in the
  normal case — see below.
- `namespace` and `submitter` are self-asserted labels, not credentials.

## Configuration

Both plugins share the same shape. Common case needs only the URL, a spec
path, and one `run`.

- **`spec`** is an explicit path; a **list** is accepted for a multi-spec
  module (each spec × each combination). No glob or auto-detect.
- **`namespace`** defaults to the module's group (`project.groupId` /
  `project.group`).
- **`submitter`** defaults to `"maven"` / `"gradle"`, unless a CI actor
  variable is set (`GITHUB_ACTOR`, `GITLAB_USER_LOGIN`, `BUILD_USER_ID`) — then
  that.
- **`optional`** omitted ⇒ the combination gates.

### Maven — `arete-ci-gate-maven-plugin`, goal `check`, phase `verify`

```xml
<plugin>
  <groupId>net.dublinux.arete</groupId>
  <artifactId>arete-ci-gate-maven-plugin</artifactId>
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
- Any non-2xx that is not `422` (unreachable, `400`/`404`, `5xx`) →
  **`MojoExecutionException`** by default; `<failOnUnavailable>false</failOnUnavailable>`
  downgrades an unreachable Areté to a warning.
- No `<failOn>` in the normal case — the policy owns pass/fail. A single
  build-wide `<failOn>` (`error` | `blocker` | `score<NN`) is accepted as an
  advanced override for holding a **stricter** bar than the policy; it is
  passed straight through on the one API call.
- Report to the log **and** `${project.build.directory}/arete-ci-gate/report.txt`
  (+ `report.json`, + `arete.sarif` when `<sarif>true</sarif>`).

### Gradle — plugin id `net.dublinux.arete.ci-gate`

```kotlin
areteCiGate {
    url = providers.gradleProperty("arete.url").orElse("http://localhost:6809")
    namespace = project.group.toString()
    submitter = "gradle"
    spec = layout.projectDirectory.file("src/main/resources/openapi.yaml")

    combination("generic-policy/Enterprise Grade")           // gating
    combination("generic-policy/Zalando") { optional = true }
}
```

- Registers `areteCiGateCheck`, wires `check.dependsOn(areteCiGateCheck)`.
- Failing non-optional verdict → task throws through Gradle's normal
  verification-failure path (`VerificationException`).
- The spec file(s) are declared as task inputs, so an unchanged spec skips the
  task via Gradle's up-to-date checking. (Maven re-runs every time in v1.)
- Report to `${layout.buildDirectory}/reports/arete-ci-gate/report.txt`
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
Areté CI Gate — module: my-service   (arete: http://localhost:6809)

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
| `arete-ci-gate-core` | HTTP client for the Automation API + the report formatter. No Maven/Gradle types. Unit-testable against a stub server. |
| `arete-ci-gate-maven-plugin` | `check` goal → `verify`, delegates to core. |
| `arete-ci-gate-gradle-plugin` | `areteCiGateCheck` task → `check`, delegates to core. |

## Non-goals

- Any local scoring logic — all of it is server-side in Areté.
- A merged/normalised score across combinations.
- Weighting, quorum, merge modes.
- Managing an Areté instance (starting/stopping a local server) — the plugin
  assumes one is reachable at the configured URL.
- Aggregating verdicts across reactor modules — each module gates itself.

## Areté API change (prerequisite)

`POST /api/v1/namespaces/{namespace}/specs` currently returns `422` for
*request rejections* too — an unknown validator/policy, an unresolvable
namespace, an unparseable spec. Before the plugins are useful, `422` must mean
**only** "the spec was scored and failed its policy":

- request rejections → `400` (bad input) or `404` (no such namespace), body
  `application/problem+json`;
- `422` reserved for a scored-but-failing result when `httpStatusOnFail=422`.

The same applies to `POST /api/v1/specs/{ref}/score`. This is a small,
backward-tolerant change to `AutomationApiController` (the error bodies are
already Problem-Details-shaped; only the status codes move).

## Repo & CI

**Decided: a separate repository with its own CI**, not modules in the `arete`
reactor. A Gradle plugin (Gradle Plugin Portal) and a Maven plugin (Maven
Central) have different release mechanics that don't sit cleanly inside
`arete`'s existing `release.yml` / mkdocs setup, and this subsystem versions
independently of the Areté app.

The new repo's CI (GitHub Actions):

- builds all three modules and runs the unit + failure-path tests on every push;
- integration-tests the Maven and Gradle plugins against a **real Areté
  instance started in the workflow** — pull the published `arete-<version>`
  release zip, run it on `localhost:6809`, point the plugins at it via the
  `arete-ci` profile / `-Parete.url`;
- on a tag, publishes `arete-ci-gate-core` + `arete-ci-gate-maven-plugin` to
  Maven Central and `arete-ci-gate-gradle-plugin` to the Gradle Plugin Portal.

## Suggested build order (checkpoint each with review)

1. `arete-ci-gate-core` — API client + report formatter, tested against a
   stub HTTP server. No build-tool code.
2. Maven plugin — `check` goal, tested against a sample `pom.xml` and a real
   local Areté; the `arete-local` / `arete-ci` profile pattern.
3. Gradle plugin — same, `build.gradle` **and** `build.gradle.kts`, the
   property-layering pattern.
4. Failure-path tests, both tools: failing gating combination; failing optional
   combination (build still passes); unreachable Areté; unknown
   validator/policy (the API returns a 4xx / an error result — surface it
   clearly).
