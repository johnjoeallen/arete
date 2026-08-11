<p align="center">
  <img src="docs/logo.png" alt="Speculate logo" width="96" height="96">
</p>

# Speculate

**[Speculate site &rarr;](https://johnjoeallen.github.io/speculate/)**

Speculate is a local-first API explorer for OpenAPI/Swagger specs. Paste a
spec, get instant browsable docs — endpoints, parameters, schemas, and
request/response examples — with search, multi-spec tabs, and light/dark
themes. No cloud, no build step for the reader, no account: it's a single
runnable jar that keeps its data on your machine.

## What It Does

Paste an OpenAPI or Swagger definition (JSON or YAML) into the textbox on
the home page and Speculate parses it, saves it locally (keyed by the
spec's `title`, so re-pasting an updated version of the same spec replaces
it rather than piling up duplicates), and renders it as browsable docs:

- Every path/method pair as a collapsible endpoint card, with HTTP-method
  colour coding and an expand/collapse-all toggle.
- Parameters (path, query, header, cookie) in a table with name, location,
  required/optional, type, and description.
- Request and response bodies as content-type tabs — one tab per media type
  in the spec (`application/json`, `application/xml`, ...), plus an
  "Object" tab showing the schema as a collapsible property tree.
- Worked examples for each media type: the spec's own `example`/`examples`
  when present, otherwise one synthesized from the schema (respecting
  `enum`, common string `format`s like `date-time`/`email`/`uuid`, nested
  objects and arrays) and rendered in the right shape for that content type
  — pretty-printed JSON, XML, or `x-www-form-urlencoded`.

Saved specs live in a sidebar you can search by title. Opening a spec adds
it to a tab bar (kept in `localStorage`) so you can flip between several
open specs without losing your place, and closing a tab just drops it from
the bar — the saved spec itself is untouched until you explicitly delete it.

## Requirements

Java 17 or later to run. Maven to build from source.

## Install

Download the latest `speculate-<version>.zip` from the
[releases page](../../releases), unzip it, and run the launcher script for
your platform:

```
unzip speculate-<version>.zip -d speculate
cd speculate
./speculate.sh        # Linux/macOS
speculate.bat          # Windows
```

Then open `http://localhost:6809`.

## Quick Start (from source)

```
./build.sh              # Linux/macOS — mvn package, then copies the jar to scripts/speculate.jar
./scripts/speculate.sh
```

```
build.bat                :: Windows
scripts\speculate.bat
```

## Configuration

| Flag | Effect |
|---|---|
| `--port PORT` / `-p PORT` | Run on `PORT` instead of the default `6809`. |
| `--wipe-db` / `--reset-db` | Delete the local database before starting, so you get a completely empty spec list. |
| `-h` / `--help` | Show usage. |

```
./scripts/speculate.sh --port 8080
./scripts/speculate.sh --wipe-db
```

Data is stored in an H2 database file under `~/.speculate/data`, regardless
of which directory you launch the script from.

## Roadmap: Custom Validation

Speculate is intended to support **pluggable custom validation** — rules
you define yourself (naming conventions, required fields, security scheme
enforcement, and similar house-style checks) that run against a pasted spec
and surface as warnings alongside the parser's own messages. This isn't
built yet; it's the next major feature planned for the project. Follow the
[issues](../../issues) for progress.

## Build From Source

```
mvn clean package
```

Produces the fat jar at `target/openapi-viewer-<version>.jar`. `build.sh`/
`build.bat` do this and copy the result to `scripts/speculate.jar` so the
launcher scripts have something to run.

## Release

Pushing a tag matching `v*.*.*` runs [`.github/workflows/release.yml`](.github/workflows/release.yml),
which sets the Maven version from the tag, builds, packages a zip
(`speculate.jar` + both launcher scripts), and publishes it as a GitHub
release.

## License

[Apache License 2.0](LICENSE).
