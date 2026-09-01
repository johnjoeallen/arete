package net.dublinux.arete.cigate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Thin client for Areté's Automation API. It submits a spec, reads each
 * combination's server-computed verdict, and returns them — it computes no
 * scores and makes no pass/fail decision beyond ANDing the non-optional
 * verdicts in {@link GateOutcome#buildPassed()}.
 *
 * <p>The submit call always sends {@code httpStatusOnFail=422} so the outcome
 * is on the status line:
 * <ul>
 *   <li>{@code 200}/{@code 201} — scored, every combination passed;</li>
 *   <li>{@code 422} with a {@code results} array — scored, something failed;</li>
 *   <li>{@code 422} without one, or any other non-2xx — {@link AreteGateException}
 *       (a build error, never a scoring failure).</li>
 * </ul>
 */
public final class AreteGateClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final Duration requestTimeout;

    public AreteGateClient() {
        this(Duration.ofSeconds(10), Duration.ofSeconds(120));
    }

    public AreteGateClient(Duration connectTimeout, Duration requestTimeout) {
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    public GateOutcome submitAndScore(GateRequest request) throws AreteGateException {
        JsonNode body = post(submitUri(request), request);
        String specId = body.path("spec").path("id").asText(null);
        List<CombinationOutcome> outcomes = readOutcomes(body, request);
        String sarif = request.sarif() ? fetchSarif(request, specId) : null;
        return new GateOutcome(specId, outcomes, sarif);
    }

    private URI submitUri(GateRequest r) {
        StringBuilder q = new StringBuilder();
        for (Combination c : r.combinations()) {
            q.append(q.isEmpty() ? '?' : '&').append("run=").append(enc(c.run()));
        }
        q.append("&httpStatusOnFail=422");
        if (r.failOn() != null) {
            q.append("&failOn=").append(enc(r.failOn()));
        }
        return URI.create(r.areteBaseUrl() + "/api/v1/namespaces/" + enc(r.namespace()) + "/specs" + q);
    }

    private JsonNode post(URI uri, GateRequest r) throws AreteGateException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", r.specContentType())
                .header("X-Arete-Submitter", r.submitter())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(r.specBytes()))
                .build();
        HttpResponse<String> res = send(req, uri);
        int status = res.statusCode();
        if (status == 200 || status == 201) {
            return parse(res.body(), uri);
        }
        if (status == 422) {
            JsonNode node = parse(res.body(), uri);
            if (node.has("results") && node.get("results").isArray()) {
                return node;
            }
            throw rejected(status, node, res.body());
        }
        throw rejected(status, tryParse(res.body()), res.body());
    }

    private HttpResponse<String> send(HttpRequest req, URI uri) throws AreteGateException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AreteGateException("Areté is not reachable at " + baseOf(uri)
                    + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AreteGateException("Interrupted while calling Areté at " + baseOf(uri), null, e);
        }
    }

    private String fetchSarif(GateRequest r, String specId) throws AreteGateException {
        if (specId == null || specId.isBlank()) {
            throw new AreteGateException("Areté did not return a spec id, cannot fetch SARIF");
        }
        URI uri = URI.create(r.areteBaseUrl() + "/api/v1/namespaces/" + enc(r.namespace())
                + "/specs/" + enc(specId) + "/scoring?format=sarif");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/sarif+json")
                .GET()
                .build();
        HttpResponse<String> res = send(req, uri);
        if (res.statusCode() != 200) {
            throw new AreteGateException("Could not fetch SARIF from " + uri + " (HTTP " + res.statusCode() + ")",
                    res.statusCode(), null);
        }
        return res.body();
    }

    private static List<CombinationOutcome> readOutcomes(JsonNode body, GateRequest request) {
        List<CombinationOutcome> outcomes = new ArrayList<>();
        for (JsonNode r : body.path("results")) {
            String validator = r.path("validator").asText("");
            String policy = r.path("policy").asText("");
            JsonNode level = r.path("level");
            outcomes.add(new CombinationOutcome(
                    validator,
                    policy,
                    isOptional(request, validator, policy),
                    r.path("status").asText("SUCCESS"),
                    numberOrNull(r, "score"),
                    r.path("grade").isNull() || r.path("grade").isMissingNode() ? null : r.path("grade").asText(),
                    numberOrNull(r, "passingScore"),
                    level.path("criterion").asText(null),
                    level.path("source").asText(null),
                    level.path("met").asBoolean(false),
                    readCounts(r.path("counts"))));
        }
        return outcomes;
    }

    private static boolean isOptional(GateRequest request, String validator, String policy) {
        String wantSlug = slug(policy);
        return request.combinations().stream()
                .filter(c -> c.validator().equalsIgnoreCase(validator))
                .filter(c -> c.policy().equalsIgnoreCase(policy) || slug(c.policy()).equals(wantSlug))
                .findFirst()
                .map(Combination::optional)
                // No match echoed back (slug/name drift): gate on it — the safe default.
                .orElse(false);
    }

    private static java.util.Map<String, Integer> readCounts(JsonNode counts) {
        if (counts == null || !counts.isObject()) {
            return java.util.Map.of();
        }
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        Iterator<java.util.Map.Entry<String, JsonNode>> it = counts.fields();
        while (it.hasNext()) {
            java.util.Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asInt());
        }
        return out;
    }

    private static Double numberOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asDouble() : null;
    }

    private AreteGateException rejected(int status, JsonNode problem, String rawBody) {
        String title = problem == null ? null : problem.path("title").asText(null);
        String detail = problem == null ? null : problem.path("detail").asText(null);
        String msg = detail != null ? detail : title != null ? title
                : rawBody == null || rawBody.isBlank() ? "no response body"
                : rawBody.length() > 500 ? rawBody.substring(0, 500) + "…" : rawBody;
        return new AreteGateException("Areté rejected the request (HTTP " + status + "): " + msg, status, null);
    }

    private JsonNode parse(String body, URI uri) throws AreteGateException {
        try {
            return JSON.readTree(body == null ? "" : body);
        } catch (IOException e) {
            throw new AreteGateException("Unreadable response from " + uri + ": " + e.getMessage(), null, e);
        }
    }

    private static JsonNode tryParse(String body) {
        try {
            return body == null ? null : JSON.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private static String baseOf(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String slug(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
