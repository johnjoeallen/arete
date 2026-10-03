# Areté Serverless Core: Plan

2026-10-02

> **Status: in progress.** Done on `merge/serverless-core`: false-positive and `$ref` fixes, the rule
> corpus and whole-API specs, Groovy removed, `arete-engine-api` / `arete-engine` split with a plain
> `Engine` class (no plugin SPI, no plugin jars, no global plugin settings). Not started: the URI policy
> loader, `.arete.yaml`, the CLI, reports, the merge-gate and the Maven/Gradle wrappers.

## Goals and requirements

Areté becomes an embeddable policy-engine library first. The server and web UI become one consumer of it, so CI can score and gate an API spec with no running service.

- **No service in CI.** Scoring runs in-process on a build agent, writes result files and sets an exit code. Nothing is deployed, which removes the "approval to deploy a service" blocker.
- **Library-first core.** Spec parsing, `$ref` resolution, Distill, policy loading, scoring, diffing and report writing live in a library with no Spring, no database and no UI.
- **Thin wrappers.** We ship a Maven plugin and a Gradle plugin that only embed the library: no scoring logic, no service call. We also ship a CLI that can score one spec, diff two specs, or write a full markdown report. Anyone else can embed the library directly.
- **Policy from any URI.** Profiles (policies), rules and matchers load from a path, `https://`, `git+https://` or Maven coordinate, with a version pin and checksum. Org-specific standards live in an internal repo, apart from the public tool.
- **Merge-gate mode.** Compare the head spec with the spec at the merge-base (target branch configurable). Report new, existing and resolved findings. Fail CI on regression or a score below threshold.
- **The server stays.** The web UI is local developer tooling, as it is today: a viewing and scoring UI that helps a developer fix findings before they commit. It is a Spring app wrapping the same library and is never part of CI.
- **Small footprint.** The engine is a normal library jar with a modest dependency tree, because it runs on build agents.

## Target architecture

```mermaid
flowchart TB
    cli["arete CLI (fat jar)<br/>score, diff, gate, report"]
    plugins["Maven and Gradle plugins<br/>thin wrappers, in-process"]
    server["arete-server<br/>local web UI: view, score, fix before commit"]

    subgraph engine["arete-engine: library with no Spring, DB or UI"]
        direction LR
        spec["Spec loader<br/>OpenAPI, $ref"]
        distill["Distill<br/>matchers, rules"]
        policy["Policy loader<br/>URI, pin, checksum"]
        scoring["Scoring<br/>score, diff, gate"]
        reports["Reports<br/>JSON, markdown, SARIF"]
        spec --> distill --> scoring --> reports
        policy --> distill
    end

    specs["Specs<br/>head file; base via git show or raw fetch"]
    content["Policy content<br/>path, https, git or Maven coordinate"]

    cli --> engine
    plugins --> engine
    server --> engine
    specs --> spec
    content --> policy
```

The CLI, third-party embedders and the server all call the same engine. The engine reads specs and policy content and writes reports; it never opens a port or a database.

## Core library and modules

Most of the engine already exists as a Spring-free module, so this is an extraction and a rename, not a rewrite. `arete-engine` holds the bundle loader, the Distill evaluator, the OpenAPI adapter and scoring on `swagger-parser`, `snakeyaml`, `re2j` and Groovy. `arete-app` holds Spring, JPA, H2 and Thymeleaf.

| Today | Becomes | Change |
| --- | --- | --- |
| `arete-engine-api` | `arete-engine-api` | Keep `Diagnostic`, `Severity`, `ScoringResult`. Add `Occurrence`, `GateResult`, `Report`. Drop the plugin-discovery framing. |
| `arete-engine` | `arete-engine` | Done: a plain `Engine` class is the entry point and the plugin interface is gone. Loader takes URIs (next section). |
| `arete-ci-gate-core`, Maven and Gradle plugins | `arete-cli`, arete-maven-plugin, arete-gradle-plugin | The HTTP client to a service is retired. The Maven and Gradle plugins and the CLI call the engine in-process. |
| `arete-app` | `arete-server` | Spring depends on `arete-engine`. Local developer UI: view and score specs, fix findings before commit. |

The public surface stays small and has no framework types:

```java
Engine engine = Engine.builder()
    .policySource(PolicySource.parse("maven:org.acme:api-policy:2.3.1#sha256=..."))
    .build();
ScoreReport head = engine.score(SpecInput.of(headPath), "EnterpriseGrade");
GateResult gate = engine.gate(base, head, GateOptions.defaults());
ReportWriter.json(gate, out); ReportWriter.markdown(gate, out);
```

**Dependency footprint.** The engine is published as a normal library jar with ordinary transitive dependencies, so embedders and build tools resolve them as usual. Today's shaded jar is a leftover of the plugin design and is not needed. Most of the engine's weight is `swagger-parser` and its dependencies (Guava, Jackson, HttpClient). Groovy, about 40% of today's jar, is removed entirely: all 52 bundled matchers have a Distill version, so its evaluator and the Matcher.groovy files go too. Shading is limited to where it is needed: the CLI is one fat jar, and the Gradle plugin relocates Jackson, Guava and SnakeYAML so they cannot clash with other build plugins. The Maven plugin needs neither, because Maven isolates plugin classloaders. Measure the CLI size after Groovy is gone.

**Rules for the engine module.** No Spring, no JPA, no servlet, no `System.exit`, no global state, no network except through the policy loader and an injectable `HttpClient`. Everything it reads comes through an interface (`SpecReader`, `PolicySource`), so tests and embedders can supply their own.

## Deep `$ref` and nested schemas

The engine's model flattens only one level of schema today, so deeply nested specs are partly invisible to rules. From `OpenApiMapAdapter`:

- Parsing uses `setResolve(true)` but not `resolveFully`, so schema `$ref`s stay as stubs. Only `requestBodies` and `responses` refs are followed by hand.
- Component schemas expose their direct properties only. A property that is a `$ref` has no type, format or description in the model, so type and description rules can misfire on it.
- Nested properties, `items` schemas and `allOf`, `oneOf` and `anyOf` members are not walked. Items are just `itemsPresent`.
- There is no cycle detection, depth limit or memoisation, because nothing recurses.

The plan:

1. **A resolved schema graph.** Each schema node carries both views: the raw `$ref` (so inline-schema rules still work) and the resolved target. Resolution follows ref-to-ref chains, memoises by target, detects cycles and has a configurable depth limit. Hitting the limit raises a finding, never a silent truncation.
2. **Walk everything.** Properties recursively, `items`, `additionalProperties`, composition members (with an `allOf` merged view), and parameters, headers, responses and request bodies, including component refs.
3. **External refs.** Relative file and URL refs resolve against the spec's base. For the merge-gate the base spec must resolve against the base commit (`git show` for each referenced file, or the raw URL at the merge-base SHA), never the head checkout, or the comparison mixes versions.
4. **Report once, at the definition.** A finding on a shared schema points at its definition pointer, with an optional "reached via" path, so one defect reached through ten paths is one occurrence. This also keeps occurrence identity stable.
5. **Nesting rules.** Add rules for maximum schema nesting depth and maximum `$ref` chain length, with configurable limits in the policy, so the pet hate becomes a measurable finding.
6. **Tests.** A corpus with deep nesting, cycles, ref-to-ref chains and external files, run before any parity numbers are trusted.

## One mechanism and richer rule semantics

**Policy engine only.** The scoring-plugin mechanism goes: no plugin discovery, registry or per-plugin settings, and no second validator type. The policy engine is the only way a spec is scored, and the `arete-engine-api` types become the engine's own API. This is separate from the Maven and Gradle build plugins, which stay as thin wrappers.

**What a rule can say today.** A matcher finds problems. Any match is a violation, and the policy attaches either a flat deduction (charged once per rule, however many matches) or `PROHIBITED` (score forced to 0). Distill has no way to say "this must be present" or "deduct more for more matches".

**What we add.** Each rule entry in a policy can set:

| Setting | Meaning |
| --- | --- |
| `expect: no-match` | Default, as today. Matches are violations. |
| `expect: match` | Must match. No match is the violation, reported at the scope's pointer (the operation, schema or spec root), and costed by `points`, `per-match` or `tiers` like any other violation. |
| `points` | Flat deduction when the rule is violated, as today. |
| `per-match` and `max` | Deduct per match, capped at `max`. |
| `tiers` | Deduction by match count, for example 1 or more costs 1, 5 or more costs 3, 20 or more costs 8. |
| `PROHIBITED` | Unchanged. Any violation forces the score to 0. |

The names and syntax are a proposal. A policy file declares a format version, so existing policies keep their current meaning.

**Distill beyond simple matching.** The matcher model needs to return more than a list of hits. Candidates, to settle in a separate Distill design note alongside `design-notes/distill-matcher-shape.md`:

- Matchers return counts and values as well as locations, so a rule can compare against a limit or across the whole spec.
- Whole-spec scope, for checks such as "at least one security scheme is defined" or "all list operations use the same pagination parameters".
- Combinators over matchers (all, any, not) so a rule can be built from smaller matchers without a new program.
- The resolved schema graph from the deep `$ref` work, so matchers see nested and referenced schemas.

**Effects on the gate.** A must-match failure reports at a stable scope pointer, so it has a normal occurrence identity. Count-based and tiered rules are compared by deduction and count, not by individual hits: they fail only if newly violated or worse than base. The report records the effective expectation and the count for each rule.

**Order of work.** These change scores, so they land after the Zally parity corpus is captured. Old and new semantics then run side by side on the golden corpus, and the corpus records any intended difference.

## Policy content from any URI

A policy source is a URI plus a pin. The loader fetches it, verifies it, and hands the engine the same in-memory bundle model it builds today from `PolicyBundle.yaml`. The existing manifest already lists `rules`, `policies` and `matchers` by path, has a `bundleId` and `bundleVersion`, and rejects unsafe paths (`..`, absolute, backslash). Those checks stay.

| Scheme | Example | Notes |
| --- | --- | --- |
| Built-in | `classpath:api-policy` | The public bundle shipped in the jar. Default when nothing is configured. |
| File or directory | `file:./policy/` | Also `~/.arete/policies` as an overlay, as now. |
| HTTPS archive | `https://host/path/policy-2.3.1.zip` | A zip or tar.gz with `PolicyBundle.yaml` at the root. |
| Maven coordinate | `maven:org.acme:api-policy:2.3.1` | Resolved from a configured repository or the GitLab package registry. |
| Git | `git+https://host/org/policy.git#v2.3.1` | A tag or commit, never a branch, when a pin is required. |

**Pin and checksum.** Each source carries a version and a `sha256`. A source with `requirePin: true` (the CI default) fails if the bundle's `bundleVersion` differs from the pin or the digest does not match. Fetched bundles are cached by digest, so a build agent downloads once and an offline run works from the cache.

**Composition.** A config can list several sources in order, such as the public base plus an internal org overlay. Later sources add or override rules, matchers and policies by ID. A policy can mark a rule `locked: true`, and the engine then refuses any `.arete.yaml` override of it, so locked rules are enforced in the engine and not by repo settings.

**Trust.** Matchers are Distill programs and are not arbitrary code. Distill is the only matcher language, so no bundle, local or remote, can run arbitrary code. Bundles from remote URIs are verified by digest before parsing. Optional signature checking (the repo already publishes `public-key.asc`) can follow later.

**Base and head share one pin.** The gate loads the policy once and scores both specs with it, so a policy upgrade cannot make old findings look new.

## CI merge-gate mode

The gate scores the head spec and the base spec with the same policy, then judges the difference, so CI never re-argues debt that already exists on the target branch.

1. **Find the changed specs.** `git diff --name-only <merge-base>...HEAD`, filtered by the configured spec globs, per app folder.
2. **Read the base without checking it out.** `git show <merge-base>:<path>`, where the merge-base is `git merge-base HEAD origin/<target>` and the target branch is configurable (default `main`). A spec that is not in the base is new.
3. **Load policy once** from the pinned source and apply the folder's `.arete.yaml` to both runs.
4. **Score base and head** in-process and match findings by occurrence identity.
5. **Classify** each finding as NEW, EXISTING or RESOLVED, apply the gate rules, write the reports and exit.

**Occurrence identity** is rule ID plus pointer, with the message left out because counts inside it change. The code check found that pointers are JSON Pointers in OpenApiMapAdapter. Paths, operations, schemas and properties are keyed by name, so they are stable. Two cases are not. Parameter pointers end in an array index (.../parameters/0), and so do tag pointers (/tags/0), so inserting a parameter or tag shifts every later index and fakes a resolved/new pair. Response pointers are the operation pointer, with no status code, so findings on different responses of one operation collide. Before gating on pointers, emit stable ones, keyed by `name` or `in`+`name` for parameters, by tag name for tags and by status code for responses. Findings with no pointer fall back to rule ID plus operation key.

| Case | Gate rule |
| --- | --- |
| Existing spec, changed | Fail on any NEW finding at error level, or if head score is lower than base score. |
| Count-limit rule | Fail only if newly violated or the count is worse than base. |
| New spec, no base | Must meet the full policy `passingScore` and have no error-level findings. |
| Spec deleted | Skipped. |
| Spec unchanged | Skipped, which keeps CI fast. |
| Base fails to parse | Treated as a new spec, with a warning. |
| Head fails to parse | Fail. |

**What goes where.** `.arete.yaml` holds deliberate, reviewed, durable deviations, each with a reason. The diff handles legacy debt automatically, so there is no accepted-issues file to maintain. This replaces the `tolerate: N` idea.

**Base spec source.** The base is a pluggable source, like policy content. Git mode reads it with `git show <merge-base>:<path>` and needs full history (`GIT_DEPTH: 0` on GitLab, `fetch-depth: 0` on GitHub). Raw mode fetches it over HTTPS from the target branch, for example the GitLab raw-file API with a CI token, so shallow clones work. Prefer pinning the raw request to the merge-base SHA the CI system provides (on GitLab, `CI_MERGE_REQUEST_DIFF_BASE_SHA`) rather than the moving tip of main. If main has moved since the branch was cut, comparing against the tip shows main's own changes as NEW or RESOLVED findings on the MR. The report records which base was used. In git mode the CLI fails with a clear message naming the setting if the base commit is unreachable.

**Showing where it regressed.** The gate decision is more than a count. Every NEW finding, and every reason the gate failed, points at a place in the spec the developer can open.

- **Pointer to file and line.** The engine maps each JSON Pointer to a line and column by composing the spec with SnakeYAML (which keeps node marks) alongside the parsed model. `Diagnostic` already has a `lineNumber` field, but nothing fills it today, and SARIF output carries only the pointer as a logical location. The new mapping fills both. NEW and EXISTING findings use head positions. RESOLVED findings use base positions, labelled as such.
- **Stable pointers do the matching.** The keyed pointers (parameters by name, responses by status) let a finding keep its identity when lines move, while the line mapping shows where it is now.
- **Reasons carry locations.** A failed gate says which rule and where: "3 NEW errors: STATUS001 at POST /orders (line 142), ...". A score drop is attributed to the findings that caused it, so "score fell 4.5" comes with the NEW findings and the deduction each cost. A worsened count rule lists the new hits.
- **Inline annotations.** The SARIF file gets real `physicalLocation` entries (file path relative to the repo root, line and column), so GitLab and GitHub can place each NEW finding on the changed line in the MR or PR. The markdown summary links `path:line` for the same findings.
- **Moved or deleted places.** A NEW finding on a definition not in the diff (for example a shared schema reached through a changed operation) is reported at the definition with its "reached via" path from the deep `$ref` work. Specs with external refs map the pointer to the file that actually contains it.
- **Changed lines only, optional.** A `--annotate-changed-only` option limits inline annotations to lines the MR touched, to keep comments short. The full report still lists everything.

## Output files and exit codes

The engine writes files and returns a decision. A separate CI step posts the comment, and Areté never calls GitLab or GitHub.

| File | Purpose |
| --- | --- |
| `arete-report.json` | Full machine report: policy ID and pin, per-spec base and head scores, every finding with status NEW, EXISTING or RESOLVED, the gate decision and the reasons. Versioned schema. |
| `arete-summary.md` | MR-comment text. One score line per spec, then one line per finding: `NEW\|EXISTING\|RESOLVED`, severity, rule, pointer, message. NEW first, and capped with a count when long. |
| `arete.sarif` | Ships in the first release, for code-scanning UIs. The server already has a SARIF renderer to reuse. |

| Exit code | Meaning |
| --- | --- |
| 0 | Gate passed, or no changed specs. |
| 1 | Gate failed: new errors, score regression, or below threshold on a new spec. |
| 2 | Usage or configuration error, including an unreachable base commit or an unverified policy. |

The exit code carries the gate decision, so the pipeline job is the required check. Running with `--report-only` writes the same files and always exits 0, for a trial period before enforcement.

## CLI, plugins and the server

The CLI is the reference front end: the plugins and the server expose the same operations.

```text
arete score  <spec> [--policy <source>] [--format json|md|sarif]
arete diff   <base-spec> <head-spec>            # score diff, no git needed
arete gate   --target origin/main [--paths 'apis/**/openapi.yaml']
arete report <spec> --out report.md             # full markdown report
arete policy verify <source>                    # check pin, checksum, matcher compilation
```

`score` and `report` work on one spec. `diff` compares two files and is the building block `gate` uses after it reads the base through `git show`. `gate` is the CI entry point.

**Maven and Gradle plugins.** Each is one goal or task (`arete:gate`, `areteGate`) that maps its configuration onto `GateOptions`, calls the engine in-process and sets build failure from the result. Neither contains scoring logic, and neither needs a service URL. They keep their own versions, as the build-gate plugins do now, and depend on the engine as an ordinary library.

**The server** is the UI as it is today, local developer tooling rather than shared infrastructure: view a spec, score it against a policy, and see the findings so the developer can fix them before committing. It keeps a local H2 store for scores. It calls `arete-engine` directly. CI never needs it, and nothing is deployed. The JSON report is a CI artifact only; the UI does not import it. Scoring locally with the same pinned policy reproduces the CI results.

The `automation-api` endpoints and the existing HTTP-client gate stay supported for one release as a deprecated path, then go.

## Staged rollout

False positives come first, because parity numbers mean nothing until `$ref` resolution is right.

1. **Stage 0: fix false positives.** Start with the content type defined through `$ref`. Add regression tests per fix, and include deep and cyclic ref resolution (see the section on deep refs).
2. **Stage 1: Zally parity batch.** Run the Global Platform specs through Areté and Zally. Map Zally rule IDs to Areté rules and classify each difference as over-reporting, under-reporting or intentional. Keep the results as a golden regression corpus in the repo.
3. **Stage 2: extract the engine.** Create `arete-engine-api` and `arete-engine`, move the server onto them, and remove Groovy. Add the URI policy loader, report writers, `.arete.yaml` overrides and the `score`, `diff` and `report` CLI commands. Remove the scoring-plugin mechanism so the policy engine is the only one. Parity corpus must still pass. Extended rule semantics (own section) come after that, as a new policy format version.
4. **Stage 3: merge-gate.** Add stable pointers, pointer-to-line mapping, occurrence matching, `gate` and the gate rules. Rebuild the Maven and Gradle plugins as thin wrappers. Use the existing Zally scripts and MR 1109 as the acceptance cases.
5. **Stage 4: host policy in-org.** Publish the policy bundle to an internal repository with a pinned version and checksum. Make the policy bundle the only supported content source. Run in `--report-only` first, then make the job required.

## Governance and risks

Areté provides the mechanism. Enforcement belongs to the organisation and its pipelines.

- **The gate is a pipeline concern.** Areté scores and returns an exit code. Whether that job is required, and what happens on failure, is set in the pipeline.
- **Teams own `.arete.yaml`.** Reviewed overrides with a reason, living next to the spec.
- **Locked rules cannot be bypassed.** The engine refuses an override of a locked rule. A team that needs different rules must select a different profile.
- **Policy bundle locations are not enforced by Areté.** It loads whatever URI and pin it is given. Developers are expected to comply with org requirements, and review of the build configuration catches deviations.
- **Adoption.** Nothing is deployed, so there is no service approval. The org still signs off on ownership, maintenance and supply chain, and has two ways to consume Areté: use the releases published on GitHub, or fork and publish its own release internally, as it did with Zally. A fork is a convenience for supply-chain control, not a divergence: changes made in the fork are pushed back upstream, so the public project stays the single line of development.

| Risk | Mitigation |
| --- | --- |
| Pointers shift on array inserts and fake new findings | Stable keyed pointers before gating; golden tests with inserted parameters. |
| Shallow CI clones | Reachability check with a clear error; document `GIT_DEPTH: 0`. |
| Plugin and engine version skew | Plugins pin an exact engine version and the report records it. |
| Policy upgrade changes scores | Pinned policy; base and head always use the same one. |
