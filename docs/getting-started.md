# Getting Started

## Requirements

- **Java 17 or later** to run.
- **Maven** to build from source.

## Install a release

Download the latest `arete-<version>.zip` from the
[releases page](https://github.com/johnjoeallen/arete/releases), unzip it,
and run the launcher script for your platform:

```bash
unzip arete-<version>.zip -d arete   # the zip has no top-level folder
cd arete
./arete.sh        # Linux/macOS
arete.bat         # Windows
```

Then open <http://localhost:6809>.

The release zip contains `arete.jar` and both launcher scripts. The
[Areté Policy Engine](scoring/policy-engine.md) is built into the jar.

## Build from source

```bash
mvn clean package
```

This produces:

- the runnable app jar at `arete-app/target/arete-<version>.jar`, which includes
  the engine (`arete-engine`)

### Quick start with the helper scripts

`build.sh` / `build.bat` run the Maven build and copy the app jar into
`scripts/` so the launcher has everything it needs:

=== "Linux/macOS"

    ```bash
    ./build.sh
    ./scripts/arete.sh
    ```

=== "Windows"

    ```bat
    build.bat
    scripts\arete.bat
    ```

Then open <http://localhost:6809>.

!!! tip "Running live checks against a throwaway database"
    Areté stores its data under `~/.arete` by default. When you are
    experimenting, use `--wipe-db` or point at a scratch home directory so you
    don't disturb a real spec collection. See [Configuration](configuration.md).
