distill(api, rule) {
    return rule.parameters["check"] == "no-query"
        ? api.paths
            .filter { path -> path.path.contains("?") }
            .map { path -> occurrence(path.pointer, path.path,
                "Path key contains '?'; declare query parameters in 'parameters' instead") }
    : rule.parameters["check"] == "no-fragment"
        ? api.paths
            .filter { path -> path.path.contains("#") }
            .map { path -> occurrence(path.pointer, path.path,
                "Path key contains '#'") }
    : [];
}
