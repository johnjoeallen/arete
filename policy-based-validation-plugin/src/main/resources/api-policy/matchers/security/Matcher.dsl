distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails
        .filter { operation ->
            !(type(operation.security == null ? api.security : operation.security) == "list"
              && size(operation.security == null ? api.security : operation.security) > 0
              && (operation.security == null ? api.security : operation.security).any { requirement ->
                    type(requirement) == "dict"
                    && requirement.keys.any { k -> k == rule.parameters["scheme"] }
                    && (size(tokenize(",", (rule.parameters["scopes"] == null ? "" : rule.parameters["scopes"]))
                              .map { s -> s.trim() }.filter { s -> s != "" }) == 0
                        ? true
                        : (type(requirement[rule.parameters["scheme"]]) == "list"
                           && tokenize(",", (rule.parameters["scopes"] == null ? "" : rule.parameters["scopes"]))
                                .map { s -> s.trim() }.filter { s -> s != "" }
                                .all { need -> requirement[rule.parameters["scheme"]].any { g -> ("" + g) == need } })) }) }
        .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path,
            size(tokenize(",", (rule.parameters["scopes"] == null ? "" : rule.parameters["scopes"]))
                    .map { s -> s.trim() }.filter { s -> s != "" }) == 0
                ? "Operation does not require security scheme " + rule.parameters["scheme"]
                : "Operation does not require security scheme " + rule.parameters["scheme"]
                    + " with scopes " + join(", ", tokenize(",", (rule.parameters["scopes"] == null ? "" : rule.parameters["scopes"]))
                        .map { s -> s.trim() }.filter { s -> s != "" })) } };
}
