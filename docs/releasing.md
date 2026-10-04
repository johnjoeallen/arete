# Releasing

## Build artifacts

```bash
mvn clean package
```

produces:

| Artifact | Path |
|---|---|
| App fat jar | `arete-app/target/arete-<version>.jar` |
| Command line | `arete-cli/target/arete-cli-<version>.jar` (one runnable jar) |
| Maven plugin | `arete-maven-plugin/target/arete-maven-plugin-<version>.jar` |
| Gradle plugin | `arete-gradle-plugin/target/arete-gradle-plugin-<version>.jar` (self-contained) |

`build.sh` / `build.bat` run this and copy the jar into `scripts/` so the
launcher scripts can run straight away. The engine (`arete-engine`) is a
dependency of the app and is inside that jar.

## Tagged releases

Pushing a tag matching `v*.*.*` runs
[`.github/workflows/release.yml`](https://github.com/johnjoeallen/arete/blob/main/.github/workflows/release.yml),
which sets the Maven version from the tag, builds, packages a zip
(`arete.jar` and both launcher scripts), and publishes it as a GitHub release together with the command line jar.

## Publishing to Maven Central

Publishing the engine, its API and the Maven and Gradle plugins to Maven Central is **not** part of the
release workflow: it doesn't belong on every tag push. It runs from
[`.github/workflows/publish.yml`](https://github.com/johnjoeallen/arete/blob/main/.github/workflows/publish.yml),
triggered by hand from the Actions tab against a specific tag once that release
is ready to be published externally.
See [Publishing to Maven Central](publishing.md) for the Central Portal setup and
release procedure.

## Documentation

This site is built with [MkDocs](https://www.mkdocs.org/) + the
[Material](https://squidfunk.github.io/mkdocs-material/) theme and deployed to
the `gh-pages` branch with [mike](https://github.com/jimporter/mike) for
versioned docs.

Preview locally:

```bash
pip install -r docs/requirements.txt
mkdocs serve
```

`.github/workflows/docs.yml` publishes on every push to `main` that touches
`docs/**` or `mkdocs.yml`, running `mike deploy --push --update-aliases
<version> latest`. The `latest` alias is the default version served at the site
root.
