# Configuration

## Launcher flags

| Flag | Effect |
|---|---|
| `--port PORT` / `-p PORT` | Run on `PORT` instead of the default `6809`. |
| `--wipe-db` / `--reset-db` | Delete the local database before starting, so you get a completely empty spec list. |
| `-h` / `--help` | Show usage. |

```bash
./scripts/speculate.sh --port 8080
./scripts/speculate.sh --wipe-db
```

The launcher scripts respect `JAVA_HOME` if it's set — checked before `java` is
resolved from `PATH`, so a machine with several JDKs installed uses the one you
point at rather than whichever `java` happens to be first on `PATH`.

## Data locations

Speculate keeps everything under `~/.speculate`, regardless of which directory
you launch from:

| Path | Contents |
|---|---|
| `~/.speculate/data` | The embedded H2 database. |
| `~/.speculate/specs` | Drop spec files here to have them loaded and watched automatically. |
| `~/.speculate/plugins` | Drop extra validation plugin jars here — see [Writing a Plugin](validation/writing-a-plugin.md). |

`~/.speculate/plugins` is created automatically on startup if it doesn't exist.
The `plugins/` folder next to `speculate.jar` (shipped in the release zip) is
**not** created automatically when missing — in a from-source dev run it would
otherwise resolve under `target/classes`.
