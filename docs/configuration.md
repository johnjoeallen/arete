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
| `~/.arete/policies` | Drop extra `*.md` policy files here to add them to the bundled [Areté Policy Engine](scoring/policy-engine.md#user-policies). |

### Deployment mode

| Property | Default | Effect |
|---|---|---|
| `arete.deployment.mode` | `local` | `shared` locks down local-filesystem features (path loading, the drop folder, `file:` URLs). |

