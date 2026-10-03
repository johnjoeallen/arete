package net.dublinux.arete.plugin;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import net.dublinux.arete.engine.api.SpecScoringPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the scoring engine this application embeds.
 *
 * <p>The engine is an ordinary dependency on the classpath, found through its
 * {@link ServiceLoader} registration; there are no plugin jars, no plugins
 * directories and no isolated classloaders. A second implementation on the
 * classpath is used as well, but only the bundled policy engine ships.
 */
@Component
public class PluginRegistry {
    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final PluginSettingsService pluginSettingsService;
    private final List<SpecScoringPlugin> candidates;
    private List<SpecScoringPlugin> plugins = List.of();

    @Autowired
    public PluginRegistry(PluginSettingsService pluginSettingsService) {
        this(pluginSettingsService, discover());
    }

    /** For tests: supply the engines directly instead of discovering them. */
    PluginRegistry(PluginSettingsService pluginSettingsService, List<SpecScoringPlugin> candidates) {
        this.pluginSettingsService = pluginSettingsService;
        this.candidates = List.copyOf(candidates);
    }

    private static List<SpecScoringPlugin> discover() {
        List<SpecScoringPlugin> found = new ArrayList<>();
        for (SpecScoringPlugin plugin : ServiceLoader.load(SpecScoringPlugin.class, SpecScoringPlugin.class.getClassLoader())) {
            found.add(plugin);
        }
        return found;
    }

    @PostConstruct
    void loadPlugins() {
        List<SpecScoringPlugin> loaded = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (SpecScoringPlugin plugin : candidates) {
            if (!seenIds.add(plugin.getId())) {
                log.warn("Ignoring a second scoring engine with id '{}'", plugin.getId());
                continue;
            }
            plugin.configure(Map.of());
            loaded.add(plugin);
            log.info("Loaded scoring engine '{}' ({})", plugin.getId(), plugin.getName());
        }
        this.plugins = Collections.unmodifiableList(loaded);
        pluginSettingsService.ensureDefaults(
                loaded.stream().map(SpecScoringPlugin::getId).collect(Collectors.toList()));
    }

    public List<SpecScoringPlugin> getPlugins() {
        return plugins;
    }
}
