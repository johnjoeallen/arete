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
| `arete policy verify` | Loads the policy sources, checks their pins and compiles every matcher; says what the bundle holds. |

## The exit code is the decision

| Code | Meaning |
|---|---|
| `0` | Everything passed. |
| `1` | The check failed: a score under the threshold, a regression, a spec that does not parse. |
| `2` | The command or its configuration is wrong: bad options, an unreadable file, a policy source that cannot be loaded or fails its pin, bad `.arete.yaml`. |

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
  --policy-source "maven:org.acme:api-policy:2.3.1#sha256=9f2c…" \
  --maven-settings default --require-pin
```

`--policy-source` is repeatable and layers: see [policy sources](scoring/policy-engine.md#policy-sources). With no
source the public bundle in the jar is used. `--maven-settings default|<path>` reads repositories, mirrors,
credentials and proxies from `settings.xml`, as the rest of CI does; `--maven-profile <id>` activates a profile;
`--require-pin` refuses a remote source without a `sha256`; `--cache-dir` / `--no-cache` control where fetched
bundles are kept; `--user-policies <dir>` adds `*.md` policies.

## Team overrides

A `.arete.yaml` next to the spec, or in a parent folder up to the repository's `.git`, is read automatically (the
nearest wins); `--overrides <file>` names one and `--no-overrides` ignores it. It holds deliberate, reviewed
deviations, each with a reason, and may name the policy for the folder. See
[team overrides](scoring/policy-engine.md#team-overrides-aretyaml). The policy is `--policy`, else the file's, else the
bundle's first.

Both specs of a `diff` are judged with the head's policy and overrides, so a difference is the spec's, not the rules'.
