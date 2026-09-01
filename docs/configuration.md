# Configuration

## Launcher flags

| Flag | Effect |
|---|---|
| `--port PORT` / `-p PORT` | Run on `PORT` instead of the default `6809`. |
| `--wipe-db` / `--reset-db` | Delete the local database before starting, so you get a completely empty spec list. |
| `-h` / `--help` | Show usage. |

```bash
./scripts/arete.sh --port 8080
./scripts/arete.sh --wipe-db
```

The launcher scripts respect `JAVA_HOME` if it's set — checked before `java` is
resolved from `PATH`, so a machine with several JDKs installed uses the one you
point at rather than whichever `java` happens to be first on `PATH`.

## Data locations

Areté keeps everything under `~/.arete`, regardless of which directory
you launch from:

| Path | Contents |
|---|---|
| `~/.arete/data` | The embedded H2 database. |
| `~/.arete/specs` | Drop spec files here to have them loaded and watched automatically. |
| `~/.arete/plugins` | Drop extra scoring plugin jars here — see [Writing a Plugin](scoring/writing-a-plugin.md). |
| `~/.arete/policies` | Drop extra `*.md` policy files here to add them to the bundled [Areté Policy Engine](scoring/policy-engine.md#user-policies). |

### Automation API settings

| Property | Default | Effect |
|---|---|---|
| `arete.deployment.mode` | `local` | `shared` locks down local-filesystem features — see the [Automation API](automation-api.md#deployment-mode). |
| `arete.api.url-fetch.allow-private` | `false` | Allow the URL fetcher to reach private/loopback addresses. Ignored in `shared` mode. |
| `arete.api.url-fetch.timeout` | `10s` | Connect/read timeout for a URL fetch. |

`~/.arete/plugins` is created automatically on startup if it doesn't exist.
The `plugins/` folder next to `arete.jar` (shipped in the release zip) is
**not** created automatically when missing — in a from-source dev run it would
otherwise resolve under `target/classes`.
