package net.dublinux.arete.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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
    void loadsAPluginJarOnlyOnceWhenBothDirectoriesResolveToTheSamePlace(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("plugins");
        Files.createDirectories(dir);
        writeFakePluginJar(dir.resolve("fake-plugin.jar"), "Fake", "fake");
        PluginRegistry registry = new PluginRegistry(dir, dir, pluginSettingsService);

        registry.loadPlugins();

        assertThat(registry.getPlugins()).hasSize(1);
    }

    @Test
    void ignoresASecondJarProvidingAPluginIdThatIsAlreadyLoaded(@TempDir Path tempDir) throws Exception {
        Path installDir = tempDir.resolve("install-plugins");
        Path userDir = tempDir.resolve("user-plugins");
        Files.createDirectories(installDir);
        Files.createDirectories(userDir);
        writeFakePluginJar(installDir.resolve("fake-plugin.jar"), "Fake", "fake");
        writeFakePluginJar(userDir.resolve("fake-plugin-copy.jar"), "Fake", "fake");
        PluginRegistry registry = new PluginRegistry(installDir, userDir, pluginSettingsService);

        registry.loadPlugins();

        assertThat(registry.getPlugins()).hasSize(1);
    }

    /** Compiles a minimal {@code SpecScoringPlugin} and packages it as a discoverable plugin jar. */
    private static void writeFakePluginJar(Path jar, String className, String pluginId) {
        try {
            Path work = Files.createTempDirectory("fake-plugin-src");
            String fqcn = "fakeplugin." + className;
            Path src = work.resolve(className + ".java");
            Files.writeString(src, """
                    package fakeplugin;
                    import java.util.*;
                    import net.dublinux.arete.scoring.spi.*;
                    public class %s implements SpecScoringPlugin {
                        public String getId() { return "%s"; }
                        public String getName() { return "%s"; }
                        public String getVersion() { return "test"; }
                        public Set<SpecFormat> getSupportedFormats() { return Set.of(SpecFormat.OPENAPI3); }
                        public void configure(Map<String,String> config) { }
                        public ScoringResult score(SpecInput input) { return ScoringResult.pluginError("unused"); }
                    }
                    """.formatted(className, pluginId, pluginId));

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            int rc = compiler.run(null, null, null,
                    "-cp", System.getProperty("java.class.path"),
                    "-d", work.toString(), src.toString());
            if (rc != 0) {
                throw new IllegalStateException("fake plugin compilation failed");
            }

            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new JarEntry("META-INF/services/net.dublinux.arete.scoring.spi.SpecScoringPlugin"));
                out.write((fqcn + "\n").getBytes());
                out.closeEntry();
                out.putNextEntry(new JarEntry("fakeplugin/" + className + ".class"));
                out.write(Files.readAllBytes(work.resolve("fakeplugin").resolve(className + ".class")));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
