distill(api, rule) {
    return tokenize(",", "check")
        .filter { unused -> !(tokenize(",", rule.parameters.allowed).any { a ->
            api.info.openapiVersion == a.trim()
            || (api.info.openapiVersion == null ? false : api.info.openapiVersion.startsWith(a.trim() + ".")) }) }
        .map { unused -> diagnostic("/info", "API",
            "Document declares unsupported or missing OpenAPI version: "
            + (api.info.openapiVersion == null ? "none" : api.info.openapiVersion)) };
}
