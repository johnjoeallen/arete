# Writing a Plugin

A validation plugin implements
[`SpecValidationPlugin`](https://github.com/johnjoeallen/arete/blob/main/arete-validation-spi/src/main/java/net/dublinux/arete/validation/spi/SpecValidationPlugin.java)
from the `arete-validation-spi` module and registers itself via
`ServiceLoader` — no Areté-specific base class or annotations required.

!!! tip "Prefer the policy bundle for rule changes"
    If you just want different **rules**, you probably don't need a new plugin
    at all. The bundled [Areté Policy Engine](policy-engine.md) is
    driven entirely by text files — add a rule, rule, or policy by editing
    Markdown, YAML, and a [Distill](distill.md) rule. Write a
    `SpecValidationPlugin` only when you need a different **engine**.

## The SPI

Add the dependency (published to Maven Central) with `provided` scope — never
by copying its source. A copy compiled into your plugin jar is a *different*
class from the host's, and `ServiceLoader` won't recognise it as implementing
`SpecValidationPlugin`.

```xml
<dependency>
    <groupId>net.dublinux.arete</groupId>
    <artifactId>arete-validation-spi</artifactId>
    <version>...</version>
    <scope>provided</scope>
</dependency>
```

Use the version matching the latest published deployment — check
[central.sonatype.com](https://central.sonatype.com) or search
`net.dublinux.arete:arete-validation-spi`. It isn't always the latest
`arete` release tag: publishing to Central is a separate, manually-triggered
step, not something every tagged release does.

### Interface shape

| Method | Purpose |
|---|---|
| `getId()` | Stable unique identifier, e.g. `"my-validator"`. |
| `getName()` | Human-readable display name. |
| `getVersion()` | Engine/plugin version string, for diagnostics. |
| `getSupportedFormats()` | `Set<SpecFormat>` — never null or empty. |
| `getRuleSets()` | `List<String>` of named rule-set variants (default: one entry, `"default"`). Order is significant. |
| `getSeverityLabel(Severity)` | Display label for one of the four fixed severities (default: title-cased enum name). |
| `configure(Map<String,String>)` | Called exactly once, before any `validate` call. |
| `validate(SpecInput)` | Validates one spec; returns a `ValidationResult`. Must not throw for expected failures — return `ValidationResult.pluginError(...)` instead. |

**Lifecycle & thread safety.** The host instantiates the plugin via its no-arg
constructor, calls `configure(...)` once, then calls `validate(...)` any number
of times, **potentially concurrently**. Don't mutate instance fields inside
`validate()`.

**Classloading contract.** Every type in a method signature must be a JDK type
or declared in the SPI module. The SPI is the shared parent classloader for
every plugin's isolated `URLClassLoader`, so those are the only types safe to
pass across the boundary. Never add a method mentioning e.g. a swagger-parser
or Kotlin type.

## A minimal plugin

```java
package com.example.myvalidator;

import net.dublinux.arete.validation.spi.*;
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
        List<Diagnostic> diagnostics = new ArrayList<>();
        // ... run your engine against input.getContent(), append Diagnostics ...
        return ValidationResult.success(diagnostics, /* rules evaluated */ -1);
    }
}
```

Register it by packaging a jar containing
`META-INF/services/net.dublinux.arete.validation.spi.SpecValidationPlugin`
with the single line:

```
com.example.myvalidator.MyValidatorPlugin
```

Then drop the jar into `~/.arete/plugins` and restart.

## Packaging

Areté loads each plugin through an **isolated, single-jar**
`URLClassLoader`, so unless your only dependency is the SPI itself, the jar
must be self-contained. Use `maven-shade-plugin` (with
`ServicesResourceTransformer` so the `META-INF/services` entries merge
correctly) to bundle your engine and its dependencies.

Because each plugin jar loads in its own classloader, dependency versions
between plugins — and between a plugin and Areté itself — never collide.

## A worked example

The bundled
[`generic-policy-validation-plugin`](https://github.com/johnjoeallen/arete/tree/main/generic-policy-validation-plugin)
is a complete, shaded `SpecValidationPlugin` — its `pom.xml`, `META-INF/services`
registration, `getRuleSets()` implementation, and packaging are the best
starting point for your own.
