package net.dublinux.arete.plugin;

import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import net.dublinux.arete.engine.api.SpecScoringPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginRegistryTest {
    private final PluginSettingsService pluginSettingsService = mock(PluginSettingsService.class);

    private static SpecScoringPlugin engine(String id) {
        return new SpecScoringPlugin() {
            @Override public String getId() { return id; }
            @Override public String getName() { return id; }
            @Override public String getVersion() { return "1"; }
            @Override public Set<SpecFormat> getSupportedFormats() { return Set.of(SpecFormat.OPENAPI3); }
            @Override public void configure(Map<String, String> config) { }
            @Override public ScoringResult score(SpecInput input) { return ScoringResult.success(List.of(), 0); }
        };
    }

    @Test
    void theEmbeddedPolicyEngineIsFoundOnTheClasspath() {
        PluginRegistry registry = new PluginRegistry(pluginSettingsService);

        registry.loadPlugins();

        assertThat(registry.getPlugins()).extracting(SpecScoringPlugin::getId).containsExactly("generic-policy");
        verify(pluginSettingsService).ensureDefaults(List.of("generic-policy"));
    }

    @Test
    void aSecondEngineWithTheSameIdIsIgnored() {
        PluginRegistry registry = new PluginRegistry(pluginSettingsService, List.of(engine("a"), engine("a"), engine("b")));

        registry.loadPlugins();

        assertThat(registry.getPlugins()).extracting(SpecScoringPlugin::getId).containsExactly("a", "b");
    }

    @Test
    void noEnginesLeavesAnEmptyRegistry() {
        PluginRegistry registry = new PluginRegistry(pluginSettingsService, List.of());

        registry.loadPlugins();

        assertThat(registry.getPlugins()).isEmpty();
        verify(pluginSettingsService).ensureDefaults(anyList());
    }
}
