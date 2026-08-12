package com.speculate.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces, at the classloader level, the exact bug that motivated {@link
 * ChildFirstClassLoader}: two jars each define a class with the same fully
 * qualified name (as happens whenever a plugin shades a third-party library
 * that the host also happens to depend on independently), and only the
 * child's own copy — not the parent's — must win.
 */
class ChildFirstClassLoaderTest {

    private static final String MARKER_CLASS_NAME = "childfirsttest.Marker";

    @Test
    void ownJarWinsOverAClassOfTheSameNameOnTheParent(@TempDir Path tempDir) throws Exception {
        Path hostJar = jarWithMarkerReturning(tempDir, "host", "host.jar");
        Path pluginJar = jarWithMarkerReturning(tempDir, "plugin", "plugin.jar");

        URLClassLoader hostLoader = new URLClassLoader(new URL[] {hostJar.toUri().toURL()}, null);
        ChildFirstClassLoader childFirst =
                new ChildFirstClassLoader(new URL[] {pluginJar.toUri().toURL()}, hostLoader);

        assertThat(callWhich(childFirst)).isEqualTo("plugin");
    }

    @Test
    void fallsBackToTheParentWhenTheChildDoesNotHaveTheClass(@TempDir Path tempDir) throws Exception {
        Path hostJar = jarWithMarkerReturning(tempDir, "host", "host.jar");
        Path emptyJar = emptyJar(tempDir, "empty.jar");

        URLClassLoader hostLoader = new URLClassLoader(new URL[] {hostJar.toUri().toURL()}, null);
        ChildFirstClassLoader childFirst =
                new ChildFirstClassLoader(new URL[] {emptyJar.toUri().toURL()}, hostLoader);

        assertThat(callWhich(childFirst)).isEqualTo("host");
    }

    private static String callWhich(ClassLoader loader) throws Exception {
        Class<?> marker = Class.forName(MARKER_CLASS_NAME, true, loader);
        Method which = marker.getMethod("which");
        return (String) which.invoke(null);
    }

    /** Compiles {@code childfirsttest.Marker#which()} returning {@code value} and packages it as a jar. */
    private static Path jarWithMarkerReturning(Path dir, String value, String jarName) throws IOException {
        Path sourceDir = Files.createDirectories(dir.resolve(jarName + "-src/childfirsttest"));
        Path sourceFile = sourceDir.resolve("Marker.java");
        Files.writeString(sourceFile, """
                package childfirsttest;
                public final class Marker {
                    public static String which() {
                        return "%s";
                    }
                }
                """.formatted(value));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int status = compiler.run(null, null, null, sourceFile.toString());
        if (status != 0) {
            throw new IllegalStateException("Failed to compile " + sourceFile);
        }
        Path classFile = sourceDir.resolve("Marker.class");

        Path jar = dir.resolve(jarName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("childfirsttest/Marker.class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }
        return jar;
    }

    private static Path emptyJar(Path dir, String jarName) throws IOException {
        Path jar = dir.resolve(jarName);
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jarOut = new JarOutputStream(out)) {
            // no entries
        }
        return jar;
    }
}
