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

A plugin can declare more than one named rule set — e.g. an
`internal`/`external` split for an organization that lints differently
depending on API audience. When an enabled plugin has more than one, Speculate
shows a picker for it on both "add a spec" forms (paste and load-file); the
choice is made once, when the spec is added, and reused on every later
validation run for that spec, including after a watched file changes. The
bundled `zally-core` plugin has two — `pedantic` (report every violation)
and `lenient` (only the `MUST`-severity ones) — as a working reference for
what a plugin does with the name it's given; a plugin with only the
implicit default set shows no picker at all. This is deliberately
engine-agnostic: a rule set is just a plugin-chosen name, not a Zally- or
any other engine-specific concept — see [Writing a custom Zally ruleset](#writing-a-custom-zally-ruleset)
for how a Zally-based plugin in particular maps that name to its own
internal mechanism.

### Writing your own plugin

A plugin implements [`SpecValidationPlugin`](speculate-validation-spi/src/main/java/net/dublinux/speculate/validation/spi/SpecValidationPlugin.java)
from the `speculate-validation-spi` module and registers itself via
`META-INF/services/net.dublinux.speculate.validation.spi.SpecValidationPlugin`
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
all. It's published to Maven Central; the coordinate is:

```xml
<dependency>
    <groupId>net.dublinux.speculate</groupId>
    <artifactId>speculate-validation-spi</artifactId>
    <version>...</version>
    <scope>provided</scope>
</dependency>
```

Use the version matching the latest published deployment — check
[central.sonatype.com](https://central.sonatype.com) or search
`net.dublinux.speculate:speculate-validation-spi`; it isn't always the
latest `speculate` release tag, since publishing to Central is a separate,
manually-triggered step, not something every tagged release does
automatically.

A minimal from-scratch plugin — no engine, just enough to compile and load —
looks like this:

```java
package com.example.myvalidator;

import net.dublinux.speculate.validation.spi.*;
import java.util.*;

public final class MyValidatorPlugin implements SpecValidationPlugin {
    @Override public String getId() { return "my-validator"; }
    @Override public String getName() { return "My Validator"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3);
    }
    @Override public void configure(Map<String, String> config) { }

    @Override public ValidationResult validate(SpecInput input) {
        List<Violation> violations = new ArrayList<>();
        // ... run your engine against input.getContent(), append Violations ...
        return ValidationResult.success(violations, /* rules evaluated */ -1);
    }
}
```

Package it as a jar with `META-INF/services/net.dublinux.speculate.validation.spi.SpecValidationPlugin`
containing the line `com.example.myvalidator.MyValidatorPlugin`, and drop it
into `~/.speculate/plugins`.

### Writing a custom Zally ruleset

If your organization already likes the Zally engine and just wants
different rules, you don't need to write a `SpecValidationPlugin` from
scratch. `zally-validation-plugin` already wraps `zally-core`; the same pattern
works for a second, independent plugin jar that bundles your own rules
instead of (or alongside) `zally-ruleset-zalando`.

A Zally rule (verified against `zally-rule-api:2.1.1` — these are Kotlin
interfaces/annotations, but ordinary Java classes implement them the same
way) has two parts:

1. A `RuleSet` — one per plugin, groups your rules under a shared ID/URL:

   ```java
   package com.example.myruleset;

   import org.zalando.zally.rule.api.RuleSet;
   import java.net.URI;

   public final class MyRuleSet implements RuleSet {
       @Override public String getId() { return "my-org"; }
       @Override public URI getUrl() { return URI.create("https://wiki.example.com/api-guidelines"); }
       @Override public URI url(org.zalando.zally.rule.api.Rule rule) { return getUrl(); }
   }
   ```

2. One class per rule, annotated `@Rule` at the class level and `@Check` on
   the method that does the checking:

   ```java
   package com.example.myruleset;

   import org.zalando.zally.rule.api.*;
   import java.util.List;

   @Rule(ruleSet = MyRuleSet.class, id = "MY100", severity = Severity.MUST, title = "No trailing slashes")
   public final class NoTrailingSlashRule {
       @Check(severity = Severity.MUST)
       public List<Violation> validate(Context context) {
           // build Violations from context.getApi(), e.g. via context.validatePaths(...)
           return List.of();
       }
   }
   ```

Register every rule class (not the `RuleSet`) in
`META-INF/services/org.zalando.zally.rule.api.Rule` — one FQCN per line,
same as `zally-ruleset-zalando` does — then wrap them exactly the way
[`ZallyValidationPlugin`](zally-validation-plugin/src/main/java/speculate/validation/zally/ZallyValidationPlugin.java)
wraps the bundled Zalando ruleset: `RulesManager.Companion.fromClassLoader(config)`
discovers rules via that same `META-INF/services` scan of the plugin's own
classloader, so — same as any other plugin — the jar needs to be
self-contained (`maven-shade-plugin`, `ServicesResourceTransformer`) and
give itself its own `getId()` so it runs alongside `zally-core` rather than
replacing it.

If a single ruleset needs to behave differently per audience (e.g. stricter
checks for externally-published APIs), that's exactly what
[`getRuleSets()`](#custom-validation) is for: declare the names, read
`input.getRuleSet()` in `validate()`, and map whichever name comes back to a
`RulesPolicy` — Zally's own ignore-list mechanism, already used in
`ZallyValidationPlugin.configure()` for the `ignoreRules` key — instead of
running every rule unconditionally.

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
