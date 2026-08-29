{ Map api, Map rule ->
    def pattern = ~(rule.parameters.pattern as String)
    (api.schemas ?: []).findAll { it.name ==~ pattern }.collect { schema ->
        [pointer: schema.pointer, path: schema.name,
         message: "Schema name '${schema.name}' is a placeholder rather than a meaningful domain name"]
    }
}
