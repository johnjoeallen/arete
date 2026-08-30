package net.dublinux.arete.service;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;

public record ParsedSpec(OpenAPI openApi, List<String> messages) {

    /** The spec's {@code info.title}, trimmed; {@code null} if absent, blank, or the spec failed to parse. */
    public String title() {
        if (openApi == null || openApi.getInfo() == null || openApi.getInfo().getTitle() == null) {
            return null;
        }
        String title = openApi.getInfo().getTitle().trim();
        return title.isEmpty() ? null : title;
    }
}
