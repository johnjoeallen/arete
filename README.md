<p align="center">
  <img src="docs/logo.png" alt="Speculate logo" width="96" height="96">
</p>

# Speculate

**[Speculate site &rarr;](https://johnjoeallen.github.io/speculate/)**

Speculate is a local-first API explorer for OpenAPI/Swagger specs. Paste a
spec, or point Speculate at one on disk, and get instant browsable docs —
endpoints, parameters, schemas, request/response examples, and pluggable
custom validation — with search, multi-spec tabs, and light/dark themes. No
cloud, no build step for the reader, no account: it's a single runnable jar
that keeps its data on your machine.

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
- `description` fields throughout the spec (info, tags, operations,
  parameters) render as Markdown, including any raw HTML they contain,
  sanitized for safe display.

You don't have to paste — Speculate can also load a spec straight from
disk and keep it in sync:

- Enter a file's full path in the "load a file from elsewhere on disk"
  field on the home page. Speculate reads it immediately and then watches
  it, so saving a change to the file updates the saved copy automatically.
- Drop a file into `~/.speculate/specs` and Speculate loads and watches it
  the same way, with no path to type — this folder is scanned on startup
  too, so files already sitting there when you launch get picked up.

Saved specs live in a sidebar you can search by title, which stays live —
a spec picked up by the file watcher appears there without a manual
refresh. Opening a spec adds it to a tab bar (kept in `localStorage`) so
you can flip between several open specs without losing your place, and
closing a tab just drops it from the bar — the saved spec itself is
untouched until you explicitly delete it.

## Requirements

Java 17 or later to run. Maven to build from source.

## Install

Download the latest `speculate-<version>.zip` from the
[releases page](../../releases), unzip it, and run the launcher script for
your platform:

```
unzip speculate-<version>.zip
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

The launcher scripts respect `JAVA_HOME` if it's set — checked before `java`
is resolved from `PATH`, so a machine with multiple JDKs installed uses the
one you point at instead of whichever `java` happens to be first on `PATH`.

Speculate keeps everything under `~/.speculate`, regardless of which
directory you launch the script from:

| Path | Contents |
|---|---|
| `~/.speculate/data` | The embedded H2 database. |
| `~/.speculate/specs` | Drop spec files here to have them loaded and watched automatically — see [What It Does](#what-it-does). |
| `~/.speculate/plugins` | Drop extra validation plugin jars here — see [Custom Validation](#custom-validation). |

## Custom Validation

Every spec you load — pasted or from a file — is run through Speculate's
validation plugins automatically. Each endpoint whose findings map to a
specific operation shows a severity-count badge (❌ error, ⚠️ warning, ℹ️ info,
💡 hint) in its header; expanding the endpoint lists those findings in full —
severity, rule ID, finding, [JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901)
location, which plugin reported it, and a "Learn more" link out to the
plugin's own rule documentation when it provides one (e.g. Zally links back
to the relevant section of the Zalando API guidelines; an organization's own
plugin could just as well link to an internal wiki page).

Plugins are `.jar` files, discovered from two folders at startup:

- `plugins/`, next to `speculate.jar` — this is where the release zip
  ships the bundled default plugin, `zally-validation-plugin`, which wraps
  Zalando's [Zally](https://github.com/zalando/zally) linter and its core
  API-guidelines ruleset. Not created automatically if missing, since in a
  from-source dev run this resolves under `target/classes` and shouldn't
  be conjured out of thin air there.
- `~/.speculate/plugins` — a stable location independent of where
  Speculate is installed, created automatically if it doesn't exist.
  Drop your own plugin jars here — e.g. an organization-specific Zally
  ruleset, packaged the same way as the bundled one but with its own
  `getId()`, so it runs alongside rather than replacing it.

Enable or disable individual plugins from **Settings**; a disabled plugin
stays loaded but is skipped during validation, so re-enabling it doesn't
need a restart.

### Writing your own plugin

A plugin implements [`SpecValidationPlugin`](speculate-validation-spi/src/main/java/speculate/validation/spi/SpecValidationPlugin.java)
from the `speculate-validation-spi` module and registers itself via
`META-INF/services/speculate.validation.spi.SpecValidationPlugin`
— standard `ServiceLoader` discovery, no Speculate-specific base class or
annotations required. Each plugin jar loads in its own isolated
classloader, so dependency versions between plugins — and between a plugin
and Speculate itself — never collide.

[`zally-validation-plugin`](zally-validation-plugin) is a fully worked
example — it's the best starting point for writing your own. One thing it
does that a from-scratch plugin usually needs to as well: Speculate loads
each plugin through an isolated, single-jar `URLClassLoader` (see
`PluginRegistry`), so unless a plugin's only dependency is the SPI itself,
its jar needs to be self-contained. `zally-validation-plugin`'s `pom.xml`
uses `maven-shade-plugin` to bundle Zally and its dependencies in; a new
plugin with real dependencies will generally need the same.

An external plugin should depend on the SPI as a real (`provided`) Maven
dependency, never by copying its source — a copy that ends up compiled into
the plugin's own jar is a *different* class from the host's, and
`ServiceLoader` won't recognize it as implementing `SpecValidationPlugin` at
all. Once published (see below), the coordinate is:

```xml
<dependency>
    <groupId>net.dublinux.speculate</groupId>
    <artifactId>speculate-validation-spi</artifactId>
    <version>...</version>
    <scope>provided</scope>
</dependency>
```

#### Publishing `speculate-validation-spi` to Maven Central

Not active yet — the release workflow's publish step is gated behind repo
secrets that don't exist until this one-time setup is done:

1. Claim the `net.dublinux` namespace at
   [central.sonatype.com](https://central.sonatype.com) (a DNS TXT record on
   `dublinux.net` proves ownership).
2. Generate a GPG keypair, publish the public key to a keyserver (e.g.
   [keys.openpgp.org](https://keys.openpgp.org)), and keep the private key +
   passphrase.
3. Add these secrets to the GitHub repo (Settings → Secrets → Actions):
   `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` (a Central Portal user
   token), `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.

Once those secrets exist, every tagged release automatically builds, signs,
and uploads `speculate-validation-spi` (only that module — not
`zally-validation-plugin` or `speculate-app`, neither of which is meant to
be an external dependency) to the Central Portal. `autoPublish` is
deliberately off: each upload sits as a pending deployment at
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
for manual review and publish, since Central doesn't allow deleting or
overwriting a release once it's live.

## Build From Source

```
mvn clean package
```

Produces the fat jar at `speculate-app/target/speculate-<version>.jar` and
the default plugin jar at
`zally-validation-plugin/target/zally-validation-plugin-<version>.jar`.
`build.sh`/`build.bat` do this and copy both into `scripts/` (the plugin
jar under `scripts/plugins/`) so the launcher scripts have everything they
need to run.

## Release

Pushing a tag matching `v*.*.*` runs [`.github/workflows/release.yml`](.github/workflows/release.yml),
which sets the Maven version from the tag, builds, packages a zip
(`speculate.jar`, both launcher scripts, and `plugins/` containing the
bundled default plugin), and publishes it as a GitHub release.

## License

[Apache License 2.0](LICENSE).
