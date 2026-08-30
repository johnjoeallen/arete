distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails
        .filter { operation ->
            (rule.parameters["operation-type"] == "create"
                && (((path.path == null ? "" : path.path) + " " + (operation.summary == null ? "" : operation.summary)).lower().contains("create")
                    || ((path.path == null ? "" : path.path) + " " + (operation.summary == null ? "" : operation.summary)).lower().contains("bulk"))
                && (operation.method != rule.parameters["expected-method"] || path.path.contains("{")))
            || (rule.parameters["target-selection"] == "search-criteria"
                && operation.method == rule.parameters["method"]
                && ((path.path == null ? "" : path.path) + " " + (operation.summary == null ? "" : operation.summary)).lower()
                    =~ /(?i)(search|filter|criteria|query)/) }
        .map { operation -> diagnostic(operation.pointer,
            operation.method + " " + path.path,
            rule.parameters["operation-type"] == "create"
                ? "Bulk creation is not POSTed to a collection"
                : "Bulk mutation uses search criteria") } };
}
