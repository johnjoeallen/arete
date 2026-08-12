package com.speculate.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PluginRegistryTest {

    private final PluginSettingsService pluginSettingsService = mock(PluginSettingsService.class);

    @Test
    void createsTheUserPluginsDirectoryWhenItDoesNotExistYet(@TempDir Path tempDir) {
        Path installDir = tempDir.resolve("install-plugins");
        Path userDir = tempDir.resolve("user-plugins");
        PluginRegistry registry = new PluginRegistry(installDir, userDir, pluginSettingsService);

        registry.loadPlugins();

        assertThat(Files.isDirectory(userDir)).isTrue();
        assertThat(registry.getPlugins()).isEmpty();
    }

    @Test
    void neverCreatesTheInstallPluginsDirectoryWhenMissing(@TempDir Path tempDir) {
        Path installDir = tempDir.resolve("install-plugins");
        Path userDir = tempDir.resolve("user-plugins");
        PluginRegistry registry = new PluginRegistry(installDir, userDir, pluginSettingsService);

        registry.loadPlugins();

        assertThat(Files.exists(installDir)).isFalse();
    }

    @Test
    void leavesAnExistingPluginsDirectoryAlone(@TempDir Path tempDir) throws Exception {
        Path installDir = tempDir.resolve("install-plugins");
        Path userDir = tempDir.resolve("user-plugins");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("not-a-jar.txt"), "irrelevant");
        PluginRegistry registry = new PluginRegistry(installDir, userDir, pluginSettingsService);

        registry.loadPlugins();

        assertThat(registry.getPlugins()).isEmpty();
        assertThat(Files.exists(userDir.resolve("not-a-jar.txt"))).isTrue();
    }

    @Test
    void exposesBothResolvedPluginsDirectories(@TempDir Path tempDir) {
        Path installDir = tempDir.resolve("install-plugins");
        Path userDir = tempDir.resolve("user-plugins");
        PluginRegistry registry = new PluginRegistry(installDir, userDir, pluginSettingsService);

        assertThat(registry.getInstallPluginsDir()).isEqualTo(installDir);
        assertThat(registry.getUserPluginsDir()).isEqualTo(userDir);
    }

}
