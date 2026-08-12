package com.speculate.domain;

/** How a {@link SpecEntity}'s content was provided. */
public enum SpecSource {
    /** Pasted as raw text; no backing file to watch. */
    PASTED,
    /** Loaded from a local file; {@link SpecEntity#getFilePath()} points at it. */
    FILE
}
