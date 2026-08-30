<p align="center">
  <img src="docs/assets/logo.png" alt="Areté logo" width="96" height="96">
</p>

# Areté

Areté stands for **API Review, Inspection & Scoring Engine**.

**[Documentation &rarr;](https://johnjoeallen.github.io/arete/)**

Areté is a local-first API explorer for OpenAPI/Swagger specs. Paste a
spec, or point Areté at one on disk, and get instant browsable docs —
endpoints, parameters, schemas, request/response examples, and pluggable
policy-driven validation — with search, multi-spec tabs, and light/dark
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

## Validation

Validation is on-demand: open a spec, pick a plugin and rule set in the
**Validation** panel, and click **Analyse**. Findings are merged per
endpoint with severity badges, JSON Pointer locations, and links to rule
docs.

The release bundles the **Areté Policy Engine**
(`generic-policy-validation-plugin`) — a policy-driven linter whose rules
and rules are plain text files, with rules running in a
safe-by-construction runtime (Distill by default, Starlark as a fallback). It
ships the Enterprise Grade,
Zalando, and Zalando Extended policies. Drop additional plugin jars into
`~/.arete/plugins`.

- [Validation overview](https://johnjoeallen.github.io/arete/validation/)
- [Policy engine](https://johnjoeallen.github.io/arete/validation/policy-engine/)
- [Rule languages](https://johnjoeallen.github.io/arete/validation/rule-languages/)
  and the [Distill reference](https://johnjoeallen.github.io/arete/validation/distill/)
- [Rule catalogue](https://johnjoeallen.github.io/arete/validation/rules/)
  and [policies](https://johnjoeallen.github.io/arete/validation/policies/)
- [Writing a plugin](https://johnjoeallen.github.io/arete/validation/writing-a-plugin/)

## Modules

| Module | Purpose |
|---|---|
| `arete-validation-spi` | Plugin SPI, published to Maven Central (`net.dublinux.arete:arete-validation-spi`). |
| `generic-policy-validation-plugin` | The bundled Areté Policy Engine. |
| `arete-app` | The Spring Boot application. |

## Release

Pushing a tag matching `v*.*.*` runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which sets
the Maven version from the tag, builds, packages the zip, and publishes it
as a GitHub release. Docs are deployed to GitHub Pages by
[`.github/workflows/docs.yml`](.github/workflows/docs.yml).

## License

[Apache License 2.0](LICENSE).
