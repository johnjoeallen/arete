<p align="center">
  <img src="docs/assets/logo.png" alt="Speculate logo" width="96" height="96">
</p>

# Speculate

**[Documentation &rarr;](https://johnjoeallen.github.io/speculate/)**

Speculate is a local-first API explorer for OpenAPI/Swagger specs. Paste a
spec, or point Speculate at one on disk, and get instant browsable docs —
endpoints, parameters, schemas, request/response examples, and pluggable
policy-driven validation — with search, multi-spec tabs, and light/dark
themes. No cloud, no build step for the reader, no account: it's a single
runnable jar that keeps its data under `~/.speculate` on your machine.

<p align="center">
  <img src="docs/assets/screenshot.png" alt="Speculate screenshot" width="800">
</p>

## Quick start

Download the latest `speculate-<version>.zip` from the
[releases page](../../releases), unzip it, and run the launcher for your
platform:

```bash
unzip speculate-<version>.zip
cd speculate
./speculate.sh        # Linux/macOS
speculate.bat         # Windows
```

Then open <http://localhost:6809>.

### From source

```bash
mvn clean package        # or ./build.sh — also copies jars into scripts/
./scripts/speculate.sh
```

Common flags: `--port PORT` / `-p PORT`, `--wipe-db`, `-h`. The launcher
respects `JAVA_HOME`. See
[Configuration](https://johnjoeallen.github.io/speculate/configuration/) for
the full list.

## Validation

Validation is on-demand: open a spec, pick a plugin and rule set in the
**Validation** panel, and click **Analyse**. Findings are merged per
endpoint with severity badges, JSON Pointer locations, and links to rule
docs.

The release bundles the **Speculate Policy Engine**
(`generic-policy-validation-plugin`) — a policy-driven linter whose rules
and detectors are plain text files, with detectors running in a
safe-by-construction Starlark runtime. It ships the Enterprise Grade,
Zalando, and Zalando Extended policies. Drop additional plugin jars into
`~/.speculate/plugins`.

- [Validation overview](https://johnjoeallen.github.io/speculate/validation/)
- [Policy engine](https://johnjoeallen.github.io/speculate/validation/policy-engine/)
- [Detector languages](https://johnjoeallen.github.io/speculate/validation/detector-languages/)
- [Rule catalogue](https://johnjoeallen.github.io/speculate/validation/rules/)
  and [policies](https://johnjoeallen.github.io/speculate/validation/policies/)
- [Writing a plugin](https://johnjoeallen.github.io/speculate/validation/writing-a-plugin/)

## Modules

| Module | Purpose |
|---|---|
| `speculate-validation-spi` | Plugin SPI, published to Maven Central (`net.dublinux.speculate:speculate-validation-spi`). |
| `generic-policy-validation-plugin` | The bundled Speculate Policy Engine. |
| `speculate-app` | The Spring Boot application. |

## Release

Pushing a tag matching `v*.*.*` runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which sets
the Maven version from the tag, builds, packages the zip, and publishes it
as a GitHub release. Docs are deployed to GitHub Pages by
[`.github/workflows/docs.yml`](.github/workflows/docs.yml).

## License

[Apache License 2.0](LICENSE).
