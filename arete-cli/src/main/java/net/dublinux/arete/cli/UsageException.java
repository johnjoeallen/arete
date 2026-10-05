package net.dublinux.arete.cli;

/** The command line is wrong: the message says how. Exit code 2. */
final class UsageException extends RuntimeException {
    UsageException(String message) { super(message); }
}
