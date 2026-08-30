distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails
        .filter { operation ->
            (rule.parameters["method"] == null || operation.method == rule.parameters["method"])
            && (rule.parameters["summary"] != "present"
                || !(operation.summary is blank))
            && (rule.parameters["summary"] != "absent"
                || operation.summary is blank)
            && (rule.parameters["description"] != "present"
                || !(operation.description is blank))
            && (rule.parameters["description"] != "absent"
                || operation.description is blank)
            && (rule.parameters["request-body"] != "present" || operation.requestBodyPresent)
            && (rule.parameters["request-body"] != "absent" || !operation.requestBodyPresent) }
        .map { operation -> occurrence(operation.pointer,
            operation.method + " " + path.path,
            rule.parameters["summary"] == "absent" ? "Operation summary is missing"
                : rule.parameters["description"] == "absent" ? "Operation description is missing"
                : rule.parameters["request-body"] == "present" ? "Operation has a request body"
                : rule.parameters["request-body"] == "absent" ? "Operation has no request body"
                : rule.parameters["method"] != null ? rule.parameters["method"] + " operation is used"
                : "Operation matches the configured rule") } };
}
