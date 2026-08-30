distill(api, rule) {
    return rule.parameters.keys.any { k -> k == "maximum-depth" }
        ? api.paths
            .filter { path -> size(pathSegments(path.path)) > rule.parameters["maximum-depth"] }
            .map { path -> occurrence(path.pointer, path.path,
                "Resource path exceeds the maximum nesting depth") }
        : truthy(rule.parameters["nested-root"])
        ? api.paths
            .filter { path -> size(pathSegments(path.path)) > 1
                && !distinct(api.paths.map { other ->
                        size(pathSegments(other.path)) > 0 ? pathSegments(other.path)[0] : null })
                    .any { root -> root == last(pathSegments(path.path)) } }
            .map { path -> occurrence(path.pointer, path.path,
                "Nested resource type is not exposed as a root resource") }
        : size(distinct(api.paths.map { path ->
                size(pathSegments(path.path)) > 0 ? pathSegments(path.path)[0] : null })) > rule.parameters["maximum"]
        ? [occurrence("/paths", "API",
            "API has "
              + ("" + size(distinct(api.paths.map { path ->
                    size(pathSegments(path.path)) > 0 ? pathSegments(path.path)[0] : null })))
              + " top-level resource types (maximum " + ("" + rule.parameters["maximum"]) + ")")]
        : [];
}
