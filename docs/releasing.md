# Releasing

## Build artifacts

```bash
mvn clean package
```

produces:

| Artifact | Path |
|---|---|
| App fat jar | `arete-app/target/arete-<version>.jar` |
| Bundled plugin jar | `policy-based-validation-plugin/target/policy-based-validation-plugin-<version>.jar` |

`build.sh` / `build.bat` run this and copy both into `scripts/` (the plugin jar
under `scripts/plugins/`) so the launcher scripts can run straight away.

## Tagged releases

Pushing a tag matching `v*.*.*` runs
[`.github/workflows/release.yml`](https://github.com/johnjoeallen/arete/blob/main/.github/workflows/release.yml),
which sets the Maven version from the tag, builds, packages a zip
(`arete.jar`, both launcher scripts, and `plugins/` with the bundled
plugin), and publishes it as a GitHub release.

## Publishing the SPI to Maven Central

Publishing `arete-validation-spi` to Maven Central is **not** part of the
release workflow — it doesn't belong on every tag push. It runs from
[`.github/workflows/publish-spi.yml`](https://github.com/johnjoeallen/arete/blob/main/.github/workflows/publish-spi.yml),
triggered by hand from the Actions tab against a specific tag once that release
is ready to be published externally.
See [Publishing the SPI](publishing-spi.md) for the Central Portal setup and
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
