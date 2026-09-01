package net.dublinux.arete.scoring.spi;

/**
 * The specification dialect being validated. Deliberately a closed enum
 * (not an open string) because the *shape* of the input (OpenAPI 3 vs.
 * Swagger 2) is a structural fact the host needs to reason about when
 * routing a spec to compatible plugins — unlike {@link Severity}, which
 * benefits from openness. Add new constants here only when a genuinely
 * new top-level spec dialect needs support (e.g. a future OPENAPI31).
 */
public enum SpecFormat {
    OPENAPI3,
    SWAGGER2
}
