package net.dublinux.arete.web.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Fetches a spec from a remote URL, server-side. {@code http}/{@code https}
 * only, with an SSRF guard (blocks loopback, any-local, link-local, site-local,
 * multicast, IPv6 unique-local and the cloud metadata address), a redirect
 * limit re-checked on every hop, a timeout, and a size cap. The guard cannot
 * be disabled in {@code shared} deployment mode.
 */
@Service
public class RemoteSpecFetcher {

    private static final int MAX_REDIRECTS = 5;

    private final DeploymentMode deploymentMode;
    private final boolean allowPrivateConfigured;
    private final long maxBytes;
    private final Duration timeout;
    private final HttpClient client;

    public RemoteSpecFetcher(
            DeploymentMode deploymentMode,
            @Value("${arete.api.url-fetch.allow-private:false}") boolean allowPrivate,
            @Value("${arete.api.url-fetch.timeout:10s}") Duration timeout,
            @Value("${arete.openapi.max-document-size:50MB}") DataSize maxDocumentSize) {
        this.deploymentMode = deploymentMode;
        this.allowPrivateConfigured = allowPrivate;
        this.timeout = timeout;
        this.maxBytes = maxDocumentSize.toBytes();
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER) // we re-guard each hop ourselves
                .build();
    }

    private boolean guardDisabled() {
        return allowPrivateConfigured && deploymentMode.isLocal();
    }

    public String fetch(String rawUrl) {
        URI uri = validate(rawUrl);
        int redirects = 0;
        try {
            while (true) {
                guardHost(uri);
                HttpResponse<byte[]> response = client.send(
                        HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse(null);
                    if (location == null) {
                        throw new FetchException("redirect with no Location from " + uri);
                    }
                    if (++redirects > MAX_REDIRECTS) {
                        throw new FetchException("too many redirects fetching " + rawUrl);
                    }
                    uri = validate(uri.resolve(location).toString());
                    continue;
                }
                if (status != 200) {
                    throw new FetchException("HTTP " + status + " fetching " + uri);
                }
                byte[] body = response.body();
                if (body == null || body.length == 0) {
                    throw new FetchException("empty response from " + uri);
                }
                if (body.length > maxBytes) {
                    throw new FetchException("spec at " + uri + " exceeds the " + maxBytes + "-byte limit");
                }
                return new String(body, StandardCharsets.UTF_8);
            }
        } catch (FetchException e) {
            throw e;
        } catch (Exception e) {
            throw new FetchException("could not fetch " + uri + ": " + e.getMessage(), e);
        }
    }

    private URI validate(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new FetchException("'" + rawUrl + "' is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new FetchException(scheme.equals("file")
                    ? "file URLs are not allowed"
                    : "only http and https URLs are supported (got '" + scheme + "')");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new FetchException("URL has no host: " + rawUrl);
        }
        return uri;
    }

    private void guardHost(URI uri) {
        if (guardDisabled()) {
            return;
        }
        String host = uri.getHost();
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FetchException("cannot resolve host '" + host + "'");
        }
        for (InetAddress a : addresses) {
            if (isBlocked(a)) {
                throw new FetchException("refusing to fetch " + host + " (" + a.getHostAddress()
                        + "): loopback / private / link-local addresses are blocked");
            }
        }
    }

    private static boolean isBlocked(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        if ("169.254.169.254".equals(a.getHostAddress())) {
            return true;
        }
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // fc00::/7 unique-local IPv6
    }

    public static final class FetchException extends RuntimeException {
        public FetchException(String message) {
            super(message);
        }

        public FetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
