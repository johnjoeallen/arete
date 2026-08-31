package net.dublinux.arete.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.web.api.Slugs;

/**
 * The browser UI's current namespace and submitter, carried in the
 * {@code arete_namespace} / {@code arete_submitter} cookies. Same self-asserted
 * labels the API uses; the UI just defaults them ({@code default} / {@code ui})
 * so it works before anyone has set anything.
 */
public record NamespaceContext(String namespace, String submitter) {

    public static NamespaceContext from(HttpServletRequest request) {
        return new NamespaceContext(
                slugOr(cookie(request, "arete_namespace"), SpecStorageService.DEFAULT_NAMESPACE),
                slugOr(cookie(request, "arete_submitter"), SpecStorageService.UI_SUBMITTER));
    }

    private static String slugOr(String raw, String fallback) {
        String slug = Slugs.normalise(raw);
        return Slugs.isValid(slug) ? slug : fallback;
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
