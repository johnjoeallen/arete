package net.dublinux.arete.engine.gate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The base fetched over HTTPS from the code host, for a CI clone too shallow to hold the base commit. The URL is a
 * template with {@code {path}} (the repository path, URL-encoded) and {@code {ref}} (the base commit or branch), such as
 * GitLab's {@code https://host/api/v4/projects/42/repository/files/{path}/raw?ref={ref}}. Pin {@code ref} to the
 * merge-base SHA the CI system provides rather than the moving tip of the branch: against the tip, changes main made after
 * the branch was cut would show up as the branch's own.
 */
public final class RawBaseSource implements BaseSource {
    private final String template;
    private final String ref;
    private final Map<String, String> headers;
    private final HttpClient http;

    public RawBaseSource(String template, String ref, Map<String, String> headers, HttpClient http) {
        if (!template.contains("{path}")) throw new GateException("the raw base URL must contain {path}");
        this.template = template;
        this.ref = ref;
        this.headers = Map.copyOf(headers);
        this.http = http != null ? http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override public Optional<String> read(String repoRelativePath) {
        String url = template.replace("{path}", URLEncoder.encode(repoRelativePath, StandardCharsets.UTF_8))
                .replace("{ref}", URLEncoder.encode(ref, StandardCharsets.UTF_8));
        URI uri = URI.create(url);
        boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new GateException("the raw base URL must use https: " + url);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET();
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() != 200) {
                throw new GateException("fetching the base of " + repoRelativePath + " returned HTTP " + response.statusCode());
            }
            return Optional.of(response.body());
        } catch (IOException e) {
            throw new GateException("could not fetch the base of " + repoRelativePath + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GateException("interrupted fetching the base of " + repoRelativePath);
        }
    }

    @Override public String description() { return "raw " + ref.substring(0, Math.min(10, ref.length())); }
}
