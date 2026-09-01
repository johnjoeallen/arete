package net.dublinux.arete.plugin;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;
import net.dublinux.arete.scoring.spi.SpecScoringPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Discovers and loads every {@link SpecScoringPlugin} jar found in either
 * of two {@code plugins/} folders:
 *
 * <ul>
 *     <li>{@code installPluginsDir} — next to the running jar (or classes
 *     dir, in an IDE/dev run). This is where the release zip ships the
 *     bundled default plugin. Read-only: never created if missing, since in
 *     a dev run this resolves under {@code target/classes} and creating it
 *     there would get swept into the next {@code mvn package} that isn't
 *     preceded by a {@code clean}.</li>
 *     <li>{@code userPluginsDir} — {@code ~/.arete/plugins}, a stable
 *     location independent of where the jar happens to be run from. Created
 *     if missing, so there's always somewhere obvious for a user to drop an
 *     extra plugin jar.</li>
 * </ul>
 *
 * Each jar gets its own isolated, <em>child-first</em> {@link URLClassLoader}
 * (see {@link ChildFirstClassLoader}), parented on the classloader that
 * loaded {@link SpecScoringPlugin} itself, per the interface's
 * classloading contract.
 *
 * <p>This is the minimal loader described for the discovery-pipeline proof:
 * one instance per plugin jar, loaded once at startup and reused for every
 * subsequent {@code score()} call. Concurrency/error-handling hardening
 * beyond the defensive {@code catch (Throwable)} below is intentionally out
 * of scope here.
 */
@Component
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final Path installPluginsDir;
    private final Path userPluginsDir;
    private final PluginSettingsService pluginSettingsService;
    private List<SpecScoringPlugin> plugins = List.of();

    @Autowired
    public PluginRegistry(
            @Value("${arete.plugins-dir}") String userPluginsDir,
            PluginSettingsService pluginSettingsService) {
        this(new ApplicationHome(PluginRegistry.class).getDir().toPath().resolve("plugins"),
                Path.of(userPluginsDir), pluginSettingsService);
    }

    PluginRegistry(Path installPluginsDir, Path userPluginsDir, PluginSettingsService pluginSettingsService) {
        this.installPluginsDir = installPluginsDir;
        this.userPluginsDir = userPluginsDir;
        this.pluginSettingsService = pluginSettingsService;
    }

    @PostConstruct
    void loadPlugins() {
        List<File> jars = new ArrayList<>();
        jars.addAll(listJars(installPluginsDir, false));
        jars.addAll(listJars(userPluginsDir, true));

        List<SpecScoringPlugin> loaded = new ArrayList<>();
        for (File jar : jars) {
            try {
                URLClassLoader isolated = new ChildFirstClassLoader(
                        new URL[] {jar.toURI().toURL()},
                        SpecScoringPlugin.class.getClassLoader());
                ServiceLoader<SpecScoringPlugin> serviceLoader =
                        ServiceLoader.load(SpecScoringPlugin.class, isolated);
                for (SpecScoringPlugin plugin : serviceLoader) {
                    plugin.configure(Map.of());
                    loaded.add(plugin);
                    log.info("Loaded scoring plugin '{}' ({}) from {}",
                            plugin.getId(), plugin.getName(), jar.getAbsolutePath());
                }
            } catch (Throwable t) {
                // Plugin jars are untrusted, dynamically loaded code and must never
                // be allowed to take down startup — this also catches things like
                // LinkageError from a classpath mismatch, not just RuntimeException.
                // Pass the throwable itself (not t.toString()) so the cause chain
                // actually reaches the log — a bare summary line has repeatedly not
                // been enough to diagnose real plugin-loading failures.
                log.warn("Failed to load scoring plugin from {}", jar.getName(), t);
            }
        }

        this.plugins = Collections.unmodifiableList(loaded);
        pluginSettingsService.ensureDefaults(
                loaded.stream().map(SpecScoringPlugin::getId).collect(Collectors.toList()));
    }

    /** Lists the jars in {@code dir}; creates it first if {@code createIfMissing}, otherwise skips a missing dir silently. */
    private static List<File> listJars(Path dir, boolean createIfMissing) {
        if (!Files.isDirectory(dir)) {
            if (!createIfMissing) {
                log.info("No plugins/ directory found at {}; skipping", dir.toAbsolutePath());
                return List.of();
            }
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                log.warn("Could not create plugins/ directory at {}: {}", dir.toAbsolutePath(), e.toString());
            }
            return List.of();
        }

        File[] jars = dir.toFile().listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null) {
            log.warn("Could not list plugins/ directory at {}", dir.toAbsolutePath());
            return List.of();
        }
        return List.of(jars);
    }

    public List<SpecScoringPlugin> getPlugins() {
        return plugins;
    }

    public Path getInstallPluginsDir() {
        return installPluginsDir;
    }

    public Path getUserPluginsDir() {
        return userPluginsDir;
    }
}
