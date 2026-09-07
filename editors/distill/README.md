# Distill editor support

Syntax highlighting for [Distill](../../docs/scoring/distill.md) — the
Groovy influenced fluent rule language in `api-policy/matchers/*/Matcher.distill`.

This directory is a VS Code extension **and** a TextMate bundle. Both editors
use the same `distill.tmLanguage.json` grammar.

## What it highlights

- keywords (`distill`, `return`, `true`, `false`) and the `is blank` operator
- the Distill-specific operators `?:`, `?.`, `==~`, `=~`, `->`
- string literals with `{{ … }}` interpolation holes (the hole re-enters the grammar)
- regex literals — `~/…/` and bare `/…/`
- bundled functions (`count(`, `words(`, …) vs. bundled members (`.summary`, `.paths`, …)
- closure parameters (`{ operation -> … }`), `api` / `rule` / `it`

It is highlighting only — no parsing, diagnostics, or completion.

Every Areté release attaches `distill-syntax-<version>.zip` (raw bundle, for
IntelliJ) and `distill-syntax-<version>.vsix` (for VS Code) — see the
[Releases page](https://github.com/johnjoeallen/arete/releases).

## Install — VS Code

**From a release:** download the `.vsix` and

```sh
code --install-extension distill-syntax-0.1.0.vsix
```

**From the repo (contributors):**

```sh
ln -s "$(pwd)/editors/distill" ~/.vscode/extensions/distill-syntax   # reload VS Code
# or build the package yourself:
cd editors/distill && npx @vscode/vsce package --no-dependencies
```

Applies to every `*.distill` file (bundled matchers are `Matcher.distill`). For
a one-off file with another name, `Change Language Mode` → `Distill`.

## Install — IntelliJ IDEA / other JetBrains IDEs

Unzip `distill-syntax-<version>.zip` (or use this `editors/distill` folder from
a checkout), then Settings → Editor → **TextMate Bundles** → `+` → select the
folder → Apply. JetBrains IDEs read the VS Code extension layout directly.

The integration binds by file extension, so `*.distill` files highlight as soon
as the bundle is added — no File Types setup. IntelliJ does not hot-reload a
changed grammar: after updating this folder, remove the bundle entry and re-add
it (or restart the IDE).

IntelliJ's TextMate engine maps only a subset of scopes to colours, so the
grammar targets the well-supported ones: functions and sequence operations
(`filter`, `map`, `occurrence`, …) render as `entity.name.function`, model
fields as `entity.other.attribute-name`, `api` / `rule` / `it` as
`variable.language`. Some fine-grained distinctions VS Code shows (known vs.
unknown members) collapse to one colour here.

## Keeping the grammar current

The `function-name` and `member-name` alternations in
`distill.tmLanguage.json` are generated from `DistillMatcherEvaluator`'s
`KNOWN_FUNCTIONS` / `KNOWN_MEMBERS`. `DistillGrammarSnapshotTest` fails if they
drift. After adding a builtin:

```sh
mvn -pl arete-policy-plugin test -Dtest=DistillGrammarSnapshotTest -Dsnapshot.update=true
```

and commit the regenerated grammar.
