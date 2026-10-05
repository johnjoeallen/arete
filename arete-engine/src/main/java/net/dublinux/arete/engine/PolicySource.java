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
 *       configured on the engine;</li>
 *   <li>{@code git:<repository>} — a bundle in a git repository ({@code https://}, {@code ssh://},
 *       {@code file://} or {@code user@host:path}), read with the {@code git} program and its own credentials.
 *       {@code ref} picks a branch, tag or commit (the default branch if left out) and {@code path} the folder
 *       holding {@code PolicyBundle.yaml} (the root if left out).</li>
 * </ul>
 *
 * <p>All pins are optional. {@code sha256} pins the exact bytes of an archive (for a git source, of the archive
 * Areté makes from the checked-out folder, which is the same for the same files); {@code version} pins the bundle's
 * {@code bundleVersion}. A pin that does not match fails the load. A git source is also pinned by a full commit id
 * as its {@code ref}.
 */
public final class PolicySource {
    private final String uri;
    private final String sha256;
    private final String version;
    private final String ref;
    private final String path;

    public PolicySource(String uri, String sha256, String version) {
        this(uri, sha256, version, null, null);
    }

    public PolicySource(String uri, String sha256, String version, String ref, String path) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.ref = ref == null || ref.isBlank() ? null : ref.strip();
        this.path = path == null || path.isBlank() ? null : path.strip();
        if ((this.ref != null || this.path != null) && !this.uri.startsWith("git:")) {
            throw new BundleValidationException("ref and path are pins for git: policy sources only: " + uri);
        }
        this.sha256 = sha256 == null || sha256.isBlank() ? null : sha256.strip().toLowerCase(java.util.Locale.ROOT);
        this.version = version == null || version.isBlank() ? null : version.strip();
        if (this.uri.isBlank()) throw new BundleValidationException("policy source must not be empty");
        if (this.sha256 != null && !this.sha256.matches("[0-9a-f]{64}")) {
            throw new BundleValidationException("policy source sha256 must be 64 hex digits: " + sha256);
        }
    }

    /** Parses {@code <uri>[#sha256=<hex>][&version=<v>][&ref=<r>][&path=<p>]}. */
    public static PolicySource parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        String uri = spec.strip();
        String sha256 = null;
        String version = null;
        String ref = null;
        String path = null;
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
                    case "ref" -> ref = value;
                    case "path" -> path = value;
                    default -> throw new BundleValidationException("unknown pin '" + key + "' in policy source " + spec);
                }
            }
        }
        return new PolicySource(uri, sha256, version, ref, path);
    }

    public String uri() { return uri; }
    public String sha256() { return sha256; }
    public String version() { return version; }
    public String ref() { return ref; }
    public String path() { return path; }

    boolean isRemote() { return uri.startsWith("https:") || uri.startsWith("http:") || uri.startsWith("maven:") || uri.startsWith("git:"); }

    @Override public String toString() {
        StringBuilder out = new StringBuilder(uri);
        char separator = '#';
        for (String[] pin : new String[][] {{"sha256", sha256}, {"version", version}, {"ref", ref}, {"path", path}}) {
            if (pin[1] == null) continue;
            out.append(separator).append(pin[0]).append('=').append(pin[1]);
            separator = '&';
        }
        return out.toString();
    }

    @Override public boolean equals(Object o) {
        return o instanceof PolicySource other && uri.equals(other.uri) && Objects.equals(sha256, other.sha256)
                && Objects.equals(version, other.version) && Objects.equals(ref, other.ref) && Objects.equals(path, other.path);
    }

    @Override public int hashCode() { return Objects.hash(uri, sha256, version, ref, path); }
}
