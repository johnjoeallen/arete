# Getting Started

## Requirements

- **Java 17 or later** to run.
- **Maven** to build from source.

## Install a release

Download the latest `arete-<version>.zip` from the
[releases page](https://github.com/johnjoeallen/arete/releases), unzip it,
and run the launcher script for your platform:

```bash
unzip arete-<version>.zip
cd arete
./arete.sh        # Linux/macOS
arete.bat         # Windows
```

Then open <http://localhost:6809>.

The release zip contains `arete.jar`, both launcher scripts, and a
`plugins/` folder holding the bundled
[Areté Policy Engine](validation/policy-engine.md).

## Build from source

```bash
mvn clean package
```

This produces:

- the runnable app jar at `arete-app/target/arete-<version>.jar`
- the bundled plugin jar at
  `generic-policy-validation-plugin/target/generic-policy-validation-plugin-<version>.jar`

### Quick start with the helper scripts

`build.sh` / `build.bat` run the Maven build and copy both jars into
`scripts/` (the plugin jar under `scripts/plugins/`) so the launcher has
everything it needs:

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
