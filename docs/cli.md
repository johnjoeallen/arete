# Command line

`arete` scores API specs without a server, so a CI job can run it as a step: score, write result files, and let the
exit code decide. It is one jar (`arete-cli-<version>.jar`, attached to each release):

```bash
java -jar arete-cli.jar score apis/orders/openapi.yaml
```

## Commands

| Command | What it does |
|---|---|
| `arete score <spec>...` | Scores each spec and prints the score, grade and findings with their file and line. |
| `arete diff <base> <head>` | Scores two versions of a spec with the same policy and says which findings are **NEW**, **EXISTING** or **RESOLVED**, and how the score moved. |
| `arete report <spec>...` | Writes a full markdown report: the score, then every finding grouped under its rule, and the overrides applied. |
| `arete gate` | The merge-gate: scores every spec, reads each changed spec's base, and judges the change rather than the spec. |
| `arete policy verify` | Loads the policy sources and compiles every matcher; says what the bundle holds. |

## The merge-gate

`arete gate` is the CI step. It does not re-argue debt a spec already has: it scores every spec on every run: a changed one as it is now
and as it was at the base, with the same policy and overrides, and judges the difference.

| Case | The gate fails when |
|---|---|
| A changed spec | it has a **new blocker** (an error-level finding, from a `PROHIBITED` rule), or its **score is lower** than the base's. |
| A new spec (no base) | it scores below the policy's pass mark, or has any blocker: it has to stand on its own. |
| A deleted spec | never: it is skipped. |
| An unchanged spec | on its own: it must have no blockers and meet the pass mark, like a new spec. Every spec is scored on every run. |
| A head that does not parse | always. |
| A base that does not parse | it is judged as a new spec, with a note. |

A failure says why and where: the rule, the file and line, and, for a lower score, which newly violated rules caused it.
A moved spec is compared with its old self. Count-based rules (limits that fail only when newly exceeded) arrive with the
richer rule semantics; until then a new finding is judged as above.

```bash
arete gate --target origin/main \
  --policy-source "maven:org.acme:api-policy:2.3.1" --maven-settings default \
  --report-md gate.md --report-json gate.json --report-sarif gate.sarif
```

- **The base** is the merge-base of `HEAD` and `--target` (default `origin/main`), or `--base-sha` when the CI system
  provides it (GitLab's `CI_MERGE_REQUEST_DIFF_BASE_SHA`). In git mode it is read with `git show <base>:<path>`: no
  checkout, and only for specs that changed. Specs are the files matching `--paths` (default `**/openapi.yaml`,
  `.yml` and `.json`), found per folder, each with its own `.arete.yaml`.
- **Shallow clones** do not hold the base commit. Either fetch the history (`GIT_DEPTH: 0` on GitLab, `fetch-depth: 0`
  on GitHub), or read the base from the code host: `--base-source raw --base-sha <sha> --raw-url '<url with {path} and
  {ref}>' --raw-header 'PRIVATE-TOKEN: $TOKEN'` (`$TOKEN` comes from the environment). Pin the base to the merge-base
  SHA, not the tip of the branch, or changes main made since the branch was cut show up as the branch's own. Raw mode
  compares every spec found with its base and drops the identical ones; `--changed-files <file>` limits it to a list.
  When the base cannot be reached the command says what to change and exits 2: that is a configuration problem, not a
  verdict.
- **Reports** are written alongside the main output: `--report-md` is the merge-request comment (the verdict, why it
  failed, then a score line and one line per finding per spec, new ones first), `--report-json` the full record, and
  `--report-sarif` the findings the change introduced, with file and line. A separate step posts them: Areté never calls
  GitLab or GitHub.
- **`--report-only`** writes the same files and always exits 0, for a trial period before the job is made required.
- **Adopting on a repository with legacy specs.** Every spec is judged, and an unchanged one has to meet the pass mark and have
  no blockers, so a legacy spec below the pass mark fails every merge until it is fixed. Start with `--report-only` to see
  the damage, then either fix those specs, limit `--paths` to the specs that are ready, or give a legacy spec a policy of its
  own in its `.arete.yaml` (a lower `passingScore`) and tighten it over time.

```yaml
# GitLab
api-gate:
  stage: test
  variables: { GIT_DEPTH: "0" }
  script:
    - git fetch origin "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME"
    - java -jar arete-cli.jar gate --target "origin/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" --maven-settings default --report-md gate.md
  artifacts: { when: always, paths: [gate.md] }
```

## The exit code is the decision

| Code | Meaning |
|---|---|
| `0` | Everything passed. |
| `1` | The check failed: a score under the threshold, a regression, a spec that does not parse. |
| `2` | The command or its configuration is wrong: bad options, an unreadable file, a policy source that cannot be loaded, bad `.arete.yaml`. |

`score` fails with `--fail-under <n>` (a number) or `--fail-under policy` (the policy's own pass mark).
`diff` fails with `--fail-on-regression` (the score fell, or a new blocker appeared). Without those flags the
commands only report. Nothing posts to GitLab or GitHub: a separate step posts the text.

## Output

`--format text|json|md|sarif` (default `text`) and `--out <file>`.

- **text**: for a terminal.
- **json**: versioned (`schemaVersion`), with every finding, its pointer, line and column, the policy and pass mark,
  and the overrides applied.
- **md**: for a merge-request comment. A diff is one line per finding, new ones first:
  `- **NEW** Warning `STATUS001` `/paths/~1orders/get` (apis/orders.yaml:42) — message`, under a score line. Long
  runs of unchanged findings are capped.
- **sarif**: SARIF 2.1.0 with the file, line and column of each finding, so a code host can annotate the line. For a
  `diff` it holds only the NEW findings, the ones worth annotating.

Findings are located from the spec's own text, for YAML and JSON. A finding about something missing (a summary that is
not there) is placed on the thing that should have it.

## Policy

```bash
arete score spec.yaml --policy "Zalando"
arete score spec.yaml \
  --policy-source classpath:api-policy \
  --policy-source "maven:org.acme:api-policy:2.3.1" \
  --maven-settings default
```

`--policy-source` is repeatable and layers: see [policy sources](scoring/policy-engine.md#policy-sources). With no
source the public bundle in the jar is used. `--maven-settings default|<path>` reads repositories, mirrors,
credentials and proxies from `settings.xml`, as the rest of CI does; `--maven-profile <id>` activates a profile;
`--cache-dir` / `--no-cache` control where fetched
bundles are kept; `--user-policies <dir>` adds `*.md` policies.

## Team overrides

A `.arete.yaml` next to the spec, or in a parent folder up to the repository's `.git`, is read automatically (the
nearest wins); `--overrides <file>` names one and `--no-overrides` ignores it. It holds deliberate, reviewed
deviations, each with a reason, and may name the policy for the folder. See
[team overrides](scoring/policy-engine.md#team-overrides-aretyaml). The policy is `--policy`, else the file's, else the
bundle's first.

Both specs of a `diff` are judged with the head's policy and overrides, so a difference is the spec's, not the rules'.
