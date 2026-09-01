<p align="center">
  <img src="docs/assets/logo.png" alt="Areté logo" width="96" height="96">
</p>

# Areté

Areté — the pursuit of API excellence.

**[Documentation &rarr;](https://johnjoeallen.github.io/arete/)**

Areté is a local-first API explorer for OpenAPI/Swagger specs. Paste a
spec, or point Areté at one on disk, and get instant browsable docs —
endpoints, parameters, schemas, request/response examples, and pluggable
policy-driven scoring — with search, multi-spec tabs, and light/dark
themes. No cloud, no build step for the reader, no account: it's a single
runnable jar that keeps its data under `~/.arete` on your machine.

<p align="center">
  <img src="docs/assets/screenshot.png" alt="Areté screenshot" width="800">
</p>

## Quick start

Download the latest `arete-<version>.zip` from the
[releases page](../../releases), unzip it, and run the launcher for your
platform:

```bash
unzip arete-<version>.zip
cd arete
./arete.sh        # Linux/macOS
arete.bat         # Windows
```

Then open <http://localhost:6809>.

### From source

```bash
mvn clean package        # or ./build.sh — also copies jars into scripts/
./scripts/arete.sh
```

Common flags: `--port PORT` / `-p PORT`, `--wipe-db`, `-h`. The launcher
respects `JAVA_HOME`. See
[Configuration](https://johnjoeallen.github.io/arete/configuration/) for
the full list.

## Scoring

Scoring is on-demand: open a spec, pick a plugin and policy in the
**Scoring** panel, and click **Score**. Findings are merged per
endpoint with severity badges, JSON Pointer locations, and links to rule
docs.

The release bundles the **Areté Policy Engine**
(`arete-policy-plugin`) — a policy-driven linter whose matchers,
rules, and policies are plain text files, with matchers written in Distill,
a safe-by-construction expression language. It ships the Enterprise Grade,
Zalando, and Zalando Extended policies. Drop additional plugin jars into
`~/.arete/plugins`.

For CI, the **Automation API** (`/api/v1`) takes a spec inline or by URL, runs
the validator/policy combinations you name, and returns findings plus a
pass/fail verdict (JSON or SARIF). No authentication — put it behind a
protected boundary.

- [Automation API](https://johnjoeallen.github.io/arete/automation-api/)
- [Scoring overview](https://johnjoeallen.github.io/arete/scoring/)
- [Policy engine](https://johnjoeallen.github.io/arete/scoring/policy-engine/)
- [Distill reference](https://johnjoeallen.github.io/arete/scoring/distill/)
- [Rule catalogue](https://johnjoeallen.github.io/arete/scoring/rules/)
  and [policies](https://johnjoeallen.github.io/arete/scoring/policies/)
- [Writing a plugin](https://johnjoeallen.github.io/arete/scoring/writing-a-plugin/)

## Modules

| Module | Purpose |
|---|---|
| `arete-scoring-spi` | Plugin SPI, published to Maven Central (`net.dublinux.arete:arete-scoring-spi`). |
| `arete-policy-plugin` | The bundled Areté Policy Engine. |
| `arete-app` | The Spring Boot application. |

## Release

Pushing a tag matching `v*.*.*` runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which sets
the Maven version from the tag, builds, packages the zip, and publishes it
as a GitHub release. Docs are deployed to GitHub Pages by
[`.github/workflows/docs.yml`](.github/workflows/docs.yml).

## License

[Apache License 2.0](LICENSE).
