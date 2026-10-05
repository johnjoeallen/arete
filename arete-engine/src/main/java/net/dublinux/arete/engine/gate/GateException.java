package net.dublinux.arete.engine.gate;

/** The gate could not run: the base cannot be reached, a source cannot be read, git is missing. A configuration problem, not a verdict. */
public final class GateException extends RuntimeException {
    public GateException(String message) { super(message); }
}
