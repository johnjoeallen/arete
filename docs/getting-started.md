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

### Build the zip locally

The release zip is assembled by CI, but you can make the same one by hand. It contains the app jar and both launchers,
with no top-level folder:

```bash
mvn clean verify                       # builds every module and runs the tests
VERSION=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)
rm -rf dist && mkdir -p dist/arete
cp arete-app/target/arete-$VERSION.jar dist/arete/arete.jar
cp arete-cli/target/arete-cli-$VERSION.jar dist/arete/arete-cli.jar   # optional: the command line
cp scripts/arete.sh scripts/arete.bat dist/arete/
chmod +x dist/arete/arete.sh
(cd dist/arete && zip -r ../../arete-$VERSION.zip .)
```

### Install the zip

Unzip it anywhere, then run the launcher for your platform. Java 17 or later must be on the `PATH` (or set
`JAVA_HOME`).

```bash
unzip arete-$VERSION.zip -d ~/arete
cd ~/arete
./arete.sh          # Linux/macOS (arete.bat on Windows)
```

Open <http://localhost:6809>. To upgrade, unzip a newer zip over the old folder; your data lives in `~/.arete`, not in
the install folder. To run the command line, use `java -jar arete-cli.jar --help`.

### Install the Maven and Gradle plugins locally

Both plugins are built by the same Maven reactor. Install everything into your local repository (`~/.m2`), skipping tests
if you like:

```bash
mvn clean install -DskipTests
```

That installs `arete-engine-api`, `arete-engine`, `arete-maven-plugin` and `arete-gradle-plugin` at the project version
(for example `0.1.0-SNAPSHOT`). To build only the plugins and what they need: `mvn install -DskipTests -pl arete-maven-plugin,arete-gradle-plugin -am`.

**Maven.** In the project you want to check, use the installed version:

```bash
mvn net.dublinux.arete:arete-maven-plugin:0.1.0-SNAPSHOT:gate -Darete.target=origin/main
```

or declare `<version>0.1.0-SNAPSHOT</version>` on the plugin as in [Maven and Gradle](build-plugins.md#maven).

**Gradle.** Add `mavenLocal()` to the plugin repositories and use the same version:

```groovy
// settings.gradle
pluginManagement {
    repositories { mavenLocal(); mavenCentral(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'net.dublinux.arete') useModule("net.dublinux.arete:arete-gradle-plugin:${requested.version}")
        }
    }
}
// build.gradle
plugins { id 'net.dublinux.arete' version '0.1.0-SNAPSHOT' }
```

Rebuild with `mvn install` after changing the plugin source; Gradle caches SNAPSHOTs, so add `--refresh-dependencies` to pick up a rebuilt one.

!!! tip "Running live checks against a throwaway database"
    Areté stores its data under `~/.arete` by default. When you are
    experimenting, use `--wipe-db` or point at a scratch home directory so you
    don't disturb a real spec collection. See [Configuration](configuration.md).
