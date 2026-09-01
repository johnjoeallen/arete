# How the build-gate design was shaped — a prompt flow

> Companion to [`build-scoring-plugins.md`](build-scoring-plugins.md). This is
> the decision trail: the questions asked, in order, and what each one settled.
> Read it to understand *why* the design looks the way it does before changing
> it.

The design was not written in one pass. It started as a port of an existing
document from a sibling project and was then narrowed, one prompt at a time,
until every open question had a decision in the body. Each step below is a
real instruction and the change it forced.

## 1. Start from the sibling project's page

**Prompt:** *"I need this document rewritten for Areté"* — pasted the Speculate
project's "distribution / build integration" page (Apache-2.0).

**Decision:** Use it as a skeleton only. Speculate's page assumed an
SPI-plugin model where scoring logic ships inside the build plugin. Areté
already has a running server with an Automation API, so the rewrite had to
target that instead. Landed as **Areté CI Gate** (see step 8).

## 2. Thin client, not a framework

**Prompt:** *"Areté has a network API that the plugins should call, we need the
URI and we need to use profiles to allow using a locally installed Areté for
local builds. So there's no new SPI."*

**Decisions:**
- The plugin is a **thin HTTP client** of `POST /api/v1/namespaces/{ns}/specs`.
  All scoring stays server-side.
- **No SPI.** Nothing is published for third parties to implement.
- **Profiles** (Maven) / **property layering** (Gradle) switch the one thing
  that varies between a laptop build and a CI build: the Areté URL. Local
  default `http://localhost:6809`.

**Follow-up prompt:** *"remove all SPI refs in this document"* — swept every
remaining mention of an SPI, plugin classpath, or `ServiceLoader`.

## 3. One status code for failure

**Prompt:** *"why do we need this, `<failOn>policy</failOn>` — the policy
should already say fail"* → then *"ok, lets always use 422 for failure"* →
later reinforced with *"only use 422 for failure, everything else ok, update
doc"*.

**Decisions:**
- The **policy owns pass/fail** (`passingScore`, `scoring: blocker | error`).
  The plugin passes **no `failOn`** in the normal case.
- The plugin **always** sends `httpStatusOnFail=422`. The HTTP status line
  carries the outcome:
  - `201` — scored, everything passed.
  - `422` — scored, at least one combination failed its policy.
  - any other non-2xx — request rejected or server error → **build error**,
    never a scoring failure.
- A single build-wide `<failOn>` survives only as an *advanced override* for
  holding a **stricter** bar than the policy.

**Consequence — the prerequisite section.** The API currently also returns
`422` for request rejections (unknown validator/policy, bad namespace,
unparseable spec). For "`422` means one thing" to hold, those must move to
`400` / `404` with `application/problem+json`. Captured as
*"Areté API change (prerequisite)"* in the design.

## 4. SARIF is opt-in, same call

**Prompt:** *"explain — `format=sarif` is available if the plugin also wants to
emit a SARIF file for CI code-scanning upload."*

**Decision:** When a SARIF file is wanted, add `&format=sarif` to the **same**
POST — no second request. Gated by `<sarif>true</sarif>` (Maven) /
`sarif = true` (Gradle). Off by default.

## 5. Package name

**Prompt:** *"package is `net.dublinux.arete`"* (correcting a `net.dublinx`
typo).

**Decision:** groupId `net.dublinux.arete`; Gradle plugin id
`net.dublinux.arete.ci-gate`; modules `arete-ci-gate-core`,
`arete-ci-gate-maven-plugin`, `arete-ci-gate-gradle-plugin`.

## 6. Separate repo, its own CI

**Prompt:** *"use CI for the plugins"* — asked to pin down where the code
lives and how it ships.

**Decision:** A **separate repository** with its own GitHub Actions CI, not
modules in the `arete` reactor. Maven Central and the Gradle Plugin Portal
have different release mechanics that don't fit `arete`'s `release.yml` /
mkdocs pipeline, and this subsystem versions independently. CI builds all
three modules, runs unit + failure-path tests on push, and integration-tests
both plugins against a **real Areté** started in the workflow from the release
zip. Tag → publish.

## 7. Fold the open questions into the body

**Prompts:** *"show me the other questions"* → *"show questions, and
resolutions"* → *"remove answered questions"*.

The doc had carried a running Decisions/open-questions table. Once each row
had an answer, the table was deleted and every resolution written into the
relevant section instead:

| Question | Resolution, now in the body |
|---|---|
| How is the spec located? | Explicit path; a list for multi-spec modules. No glob or auto-detect. |
| Default namespace? | The module's group (`project.groupId` / `project.group`). |
| Default submitter label? | `"maven"` / `"gradle"`, unless a CI actor var is set (`GITHUB_ACTOR`, `GITLAB_USER_LOGIN`, `BUILD_USER_ID`). |
| How is a combination excluded from the gate? | `optional` — omitted ⇒ it gates. |
| Caching / up-to-date? | Gradle declares the spec as a task input, so an unchanged spec skips the task. Maven re-runs every time in v1. |
| Reactor aggregation? | Non-goal. Each module gates itself. |

## 8. The name

**Prompt:** *"arete-ci-gate"*.

**Decision:** settled. The subsystem is **Areté CI Gate**; artifacts
`arete-ci-gate-core`, `arete-ci-gate-maven-plugin`,
`arete-ci-gate-gradle-plugin`; Gradle plugin id `net.dublinux.arete.ci-gate`;
Gradle extension `areteCiGate { }`, task `areteCiGateCheck`. The doc no longer
carries an open name question.

## The shape of the decisions

Every prompt in this flow pushed in the same direction: **move logic to the
server, keep the plugin dumb.** The plugin submits a spec, reads a verdict,
ANDs the non-optional results, and fails the build. It computes nothing. When
in doubt about a feature, the answer that won was the one that kept the plugin
a thin client.
