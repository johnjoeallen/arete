package net.dublinux.arete.engine;

/** A policy bundle, source or override that cannot be loaded or is not valid. */
public final class BundleValidationException extends RuntimeException {
    BundleValidationException(String message) { super(message); }
}
