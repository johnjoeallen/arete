distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails.expand { op ->
        (op.security == null ? [] : op.security).expand { req -> req.keys
            .filter { scheme -> !api.components.securitySchemes.any { d -> d == scheme } }
            .map { scheme -> occurrence(op.pointer, op.method + " " + path.path,
                "Security requirement names scheme '" + scheme
                    + "', which is not defined in components.securitySchemes") } } } }
    + (api.security == null ? [] : api.security).expand { req -> req.keys
        .filter { scheme -> !api.components.securitySchemes.any { d -> d == scheme } }
        .map { scheme -> occurrence("/security", "API",
            "Global security requirement names scheme '" + scheme
                + "', which is not defined in components.securitySchemes") } };
}
