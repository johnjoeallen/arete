package com.speculate.plugin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A {@link URLClassLoader} that checks its own jar(s) before delegating to
 * its parent, instead of the JDK default's parent-first order.
 *
 * <p>Plain parent-first {@code URLClassLoader} isn't actually enough
 * isolation on its own: if a class of the same name happens to also be
 * reachable from the parent (the host app's classloader) — not because a
 * plugin is <em>trying</em> to depend on the host, but simply because both
 * happen to transitively pull in the same third-party library, e.g. both
 * Speculate and a shaded Zally-based plugin depending on
 * {@code com.github.java-json-tools:msg-simple} — parent-first delegation
 * silently hands the plugin the <strong>host's</strong> copy of that class
 * instead of the one bundled in its own jar. That defeated the isolation
 * guarantee in exactly this way once already: the host's copy of
 * {@code PropertiesMessageSource} resolved {@code Class.getResource(...)}
 * against the host's classpath, which doesn't have the resource file only
 * the plugin jar bundles, and threw {@code IOException}. Checking the
 * plugin's own jar first means the plugin's classes are only ever the ones
 * actually shaded into it.
 *
 * <p>The SPI classes themselves are the deliberate exception, and don't need
 * special-casing here to remain one: plugins declare
 * {@code speculate-validation-spi} as a {@code provided} (compile-only)
 * dependency specifically so it's never shaded into their jar, so
 * {@code findClass} below can never find them — every lookup for an SPI type
 * falls through to the parent, which is exactly the identity the host's
 * {@code ServiceLoader.load(SpecValidationPlugin.class, ...)} call needs.
 */
final class ChildFirstClassLoader extends URLClassLoader {

    ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException e) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
