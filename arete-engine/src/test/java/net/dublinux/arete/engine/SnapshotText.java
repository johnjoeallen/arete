package net.dublinux.arete.engine;

/**
 * Normalises text before a snapshot comparison so a diff is never just
 * Windows-vs-Unix line endings or stray whitespace: CRLF/CR become LF,
 * trailing whitespace is dropped from every line, and trailing blank lines
 * are dropped from the end.
 */
final class SnapshotText {
    private SnapshotText() {}

    static String normalise(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[ \\t]+(?=\n|$)", "")
                .stripTrailing();
    }
}
