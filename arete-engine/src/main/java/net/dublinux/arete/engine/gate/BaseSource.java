package net.dublinux.arete.engine.gate;

import java.util.Optional;

/** Where the base version of a spec comes from: the text of a file as it was on the target branch. */
public interface BaseSource {
    /** The file's text at the base, or empty when it did not exist there (a new spec). */
    Optional<String> read(String repoRelativePath);

    /** A short description for reports, such as {@code merge-base 1a2b3c4}. */
    String description();
}
