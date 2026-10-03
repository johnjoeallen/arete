package net.dublinux.arete.engine;

import java.util.Objects;

/**
 * Where a policy bundle comes from, and what it must be.
 *
 * <p>A source is a URI plus an optional pin, written {@code <uri>[#sha256=<hex>][&version=<v>]}:
 *
 * <ul>
 *   <li>{@code classpath:api-policy} — a bundle inside the running jar (the public default);</li>
 *   <li>{@code file:./policy/} — a directory, or a {@code .zip}, on disk;</li>
 *   <li>{@code https://host/path/policy-2.3.1.zip} — a zip fetched over HTTPS (plain HTTP is accepted for
 *       loopback hosts only);</li>
 *   <li>{@code maven:group:artifact:version[:packaging]} — a zip (or jar) in a Maven-layout repository
 *       configured on the engine.</li>
 * </ul>
 *
 * <p>{@code sha256} pins the exact bytes of an archive; {@code version} pins the bundle's
 * {@code bundleVersion}. A pin that does not match fails the load.
 */
public final class PolicySource {
    private final String uri;
    private final String sha256;
    private final String version;

    public PolicySource(String uri, String sha256, String version) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.sha256 = sha256 == null || sha256.isBlank() ? null : sha256.strip().toLowerCase(java.util.Locale.ROOT);
        this.version = version == null || version.isBlank() ? null : version.strip();
        if (this.uri.isBlank()) throw new BundleValidationException("policy source must not be empty");
        if (this.sha256 != null && !this.sha256.matches("[0-9a-f]{64}")) {
            throw new BundleValidationException("policy source sha256 must be 64 hex digits: " + sha256);
        }
    }

    /** Parses {@code <uri>[#sha256=<hex>][&version=<v>]}. */
    public static PolicySource parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        String uri = spec.strip();
        String sha256 = null;
        String version = null;
        int hash = uri.indexOf('#');
        if (hash >= 0) {
            String pins = uri.substring(hash + 1);
            uri = uri.substring(0, hash);
            for (String pin : pins.split("&")) {
                int eq = pin.indexOf('=');
                String key = eq < 0 ? pin : pin.substring(0, eq);
                String value = eq < 0 ? "" : pin.substring(eq + 1);
                switch (key) {
                    case "sha256" -> sha256 = value;
                    case "version" -> version = value;
                    default -> throw new BundleValidationException("unknown pin '" + key + "' in policy source " + spec);
                }
            }
        }
        return new PolicySource(uri, sha256, version);
    }

    public String uri() { return uri; }
    public String sha256() { return sha256; }
    public String version() { return version; }

    boolean isRemote() { return uri.startsWith("https:") || uri.startsWith("http:") || uri.startsWith("maven:"); }

    @Override public String toString() {
        return uri + (sha256 == null ? "" : "#sha256=" + sha256) + (version == null ? "" : (sha256 == null ? "#" : "&") + "version=" + version);
    }

    @Override public boolean equals(Object o) {
        return o instanceof PolicySource other && uri.equals(other.uri)
                && Objects.equals(sha256, other.sha256) && Objects.equals(version, other.version);
    }

    @Override public int hashCode() { return Objects.hash(uri, sha256, version); }
}
