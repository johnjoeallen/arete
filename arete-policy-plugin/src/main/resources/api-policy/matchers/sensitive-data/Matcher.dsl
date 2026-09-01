distill(api, rule) {
    return rule.scope == "property"
        ? api.schemas.expand { schema -> schema.properties
            .filter { prop -> prop.name =~ ("(?i)" + rule.parameters.pattern) }
            .map { prop -> occurrence(prop.pointer, prop.name,
                "Schema property name may contain sensitive data: " + prop.name) } }
        : api.paths.expand { path -> path.operationDetails
            .expand { operation -> operation.parameters
                .filter { param -> param.in
                        == (rule.scope == "query-parameter" ? "query"
                            : rule.scope == "path-parameter" ? "path" : "header")
                    && param.name =~ ("(?i)" + rule.parameters.pattern) }
                .map { param -> occurrence(param.pointer,
                    operation.method + " " + path.path,
                    "Parameter name may expose sensitive data: " + param.name) } } };
}
