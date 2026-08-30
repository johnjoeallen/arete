package net.dublinux.arete.service;

import net.dublinux.arete.web.dto.EndpointView;
import net.dublinux.arete.web.dto.TagGroup;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups a spec's operations by tag, in the order tags are declared in the
 * spec's top-level {@code tags:} list (tags used on an operation but never
 * declared there are appended in first-seen order). An operation with
 * multiple tags appears once per tag, matching Swagger UI's convention.
 * Untagged operations are collected into a trailing "Other" group.
 */
public final class EndpointGrouper {

    private static final String UNTAGGED = "Other";

    private EndpointGrouper() {
    }

    public static List<TagGroup> group(OpenAPI openApi) {
        if (openApi == null || openApi.getPaths() == null) {
            return List.of();
        }

        Map<String, String> declaredDescriptions = new LinkedHashMap<>();
        if (openApi.getTags() != null) {
            for (Tag tag : openApi.getTags()) {
                if (tag.getName() != null) {
                    declaredDescriptions.put(tag.getName(), tag.getDescription());
                }
            }
        }

        Map<String, List<EndpointView>> byTag = new LinkedHashMap<>();
        declaredDescriptions.keySet().forEach(name -> byTag.put(name, new ArrayList<>()));

        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            Map<PathItem.HttpMethod, Operation> operations = pathEntry.getValue().readOperationsMap();
            if (operations == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
                EndpointView endpoint = new EndpointView(pathEntry.getKey(), opEntry.getKey().name(), opEntry.getValue());
                List<String> tags = opEntry.getValue().getTags();
                if (tags == null || tags.isEmpty()) {
                    byTag.computeIfAbsent(UNTAGGED, k -> new ArrayList<>()).add(endpoint);
                } else {
                    for (String tag : tags) {
                        byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(endpoint);
                    }
                }
            }
        }

        List<TagGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<EndpointView>> entry : byTag.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String name = entry.getKey();
            String description = UNTAGGED.equals(name) ? null : declaredDescriptions.get(name);
            groups.add(new TagGroup(name, description, entry.getValue()));
        }

        // Stable sort: keeps declared/first-seen order among tagged groups, moves "Other" to the end.
        groups.sort((a, b) -> Boolean.compare(UNTAGGED.equals(a.name()), UNTAGGED.equals(b.name())));

        return groups;
    }

}
