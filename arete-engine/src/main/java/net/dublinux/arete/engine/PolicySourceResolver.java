package net.dublinux.arete.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link PolicySource} into bundle resources, fetching and verifying what is remote.
 *
 * <p>An archive is identified by its SHA-256. A pinned source is looked up in the cache by that digest
 * first, so a build agent that has fetched it once can run offline; whatever is fetched or read from the
 * cache is hashed again and must match the pin. With {@code requirePin}, a remote source with no
 * {@code sha256} is refused before anything is downloaded.
 */
final class PolicySourceResolver {
    static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024;

    private final HttpClient http;
    private final Path cacheDir;
    private final List<MavenSettings.Repository> mavenRepositories;
    private final Map<String, String> headers;
    private final boolean requirePin;
    private final ClassLoader classLoader;

    PolicySourceResolver(HttpClient http, Path cacheDir, List<MavenSettings.Repository> mavenRepositories, Map<String, String> headers,
            boolean requirePin, ClassLoader classLoader) {
        this.http = http != null ? http : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.cacheDir = cacheDir;
        this.mavenRepositories = List.copyOf(mavenRepositories);
        this.headers = Map.copyOf(headers);
        this.requirePin = requirePin;
        this.classLoader = classLoader;
    }

    BundleResources resolve(PolicySource source) {
        String uri = source.uri();
        if (uri.startsWith("classpath:")) {
            return new ClasspathBundleResources(classLoader, uri.substring("classpath:".length()));
        }
        if (uri.startsWith("file:")) {
            return fromFile(localPath(uri), source);
        }
        if (source.isRemote()) {
            if (requirePin && source.sha256() == null && !GitPolicyFetcher.pinnedByCommit(source)) {
                throw new BundleValidationException("policy source " + uri + " has no sha256 pin"
                        + (uri.startsWith("git:") ? " or commit ref" : "") + ", and pins are required");
            }
            byte[] archive = archiveFor(source);
            return ZipBundleResources.of(archive, uri);
        }
        throw new BundleValidationException("unsupported policy source '" + uri
                + "'; use classpath:, file:, https:, maven: or git:");
    }

    private BundleResources fromFile(Path path, PolicySource source) {
        if (Files.isDirectory(path)) return new DirectoryBundleResources(path);
        if (Files.isRegularFile(path)) {
            byte[] archive = readFile(path);
            verifyPin(source, archive);
            return ZipBundleResources.of(archive, path.toString());
        }
        throw new BundleValidationException("policy source " + source.uri() + " does not exist");
    }

    private byte[] archiveFor(PolicySource source) {
        if (source.sha256() != null && cacheDir != null) {
            Path cached = cacheDir.resolve(source.sha256() + ".zip");
            if (Files.isRegularFile(cached)) {
                byte[] bytes = readFile(cached);
                if (sha256(bytes).equals(source.sha256())) return bytes;
                // A damaged cache entry is not trusted; fall through and fetch again.
            }
        }
        byte[] bytes = source.uri().startsWith("maven:") ? fetchMaven(source.uri())
                : source.uri().startsWith("git:") ? GitPolicyFetcher.fetch(source)
                : fetch(URI.create(source.uri()), Map.of());
        verifyPin(source, bytes);
        if (cacheDir != null) store(sha256(bytes), bytes);
        return bytes;
    }

    private void verifyPin(PolicySource source, byte[] archive) {
        if (source.sha256() != null && !source.sha256().equals(sha256(archive))) {
            throw new BundleValidationException("policy source " + source.uri() + " does not match its pin: expected sha256 "
                    + source.sha256() + " but it is " + sha256(archive));
        }
    }

    private byte[] fetchMaven(String coordinate) {
        String[] parts = coordinate.substring("maven:".length()).split(":");
        if (parts.length < 3 || parts.length > 4) {
            throw new BundleValidationException("maven policy source must be maven:group:artifact:version[:packaging], got " + coordinate);
        }
        if (mavenRepositories.isEmpty()) {
            throw new BundleValidationException("policy source " + coordinate + " needs a Maven repository: configure one on the engine");
        }
        String packaging = parts.length == 4 ? parts[3] : "zip";
        String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + "." + packaging;
        BundleValidationException last = null;
        for (MavenSettings.Repository repository : mavenRepositories) {
            String base = repository.url().endsWith("/") ? repository.url() : repository.url() + "/";
            try {
                if (base.startsWith("file:")) {
                    Path file = localPath(base + path);
                    if (Files.isRegularFile(file)) return readFile(file);
                    last = new BundleValidationException("not found in " + repository.url());
                } else {
                    return fetch(URI.create(base + path), repository.headers());
                }
            } catch (BundleValidationException e) {
                last = e;
            }
        }
        throw new BundleValidationException("policy source " + coordinate + " was not found in any configured repository"
                + (last == null ? "" : " (" + last.getMessage() + ")"));
    }

    private byte[] fetch(URI uri, Map<String, String> extraHeaders) {
        boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new BundleValidationException("policy source " + uri + " must use https");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET();
        headers.forEach(request::header);
        extraHeaders.forEach(request::header);
        try {
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new BundleValidationException("policy source " + uri + " returned HTTP " + response.statusCode());
                }
                byte[] bytes = body.readNBytes((int) MAX_ARCHIVE_BYTES + 1);
                if (bytes.length > MAX_ARCHIVE_BYTES) throw new BundleValidationException("policy source " + uri + " is too large");
                return bytes;
            }
        } catch (IOException e) {
            throw new BundleValidationException("policy source " + uri + " could not be fetched: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BundleValidationException("policy source " + uri + " fetch was interrupted");
        }
    }

    private void store(String digest, byte[] bytes) {
        try {
            Files.createDirectories(cacheDir);
            Path target = cacheDir.resolve(digest + ".zip");
            Path temporary = Files.createTempFile(cacheDir, digest, ".part");
            Files.write(temporary, bytes);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // The cache is an optimisation; a read-only home must not stop scoring.
            System.getLogger(PolicySourceResolver.class.getName()).log(System.Logger.Level.WARNING,
                    "Could not cache policy bundle in {0}: {1}", cacheDir, e.getMessage());
        }
    }

    private static Path localPath(String fileUri) {
        String rest = fileUri.substring("file:".length());
        if (rest.startsWith("//")) return Path.of(URI.create(fileUri));
        return Path.of(rest);
    }

    private static byte[] readFile(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_ARCHIVE_BYTES) throw new BundleValidationException(file + " is too large to be a policy bundle");
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BundleValidationException("could not read " + file + ": " + e.getMessage());
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
