{ Map api, Map rule ->
    def p = rule.parameters
    def pattern = ~(p.pattern as String)
    def requirePascal = p.case == 'pascal-case'
    def pascal = ~/[A-Z][A-Za-z0-9]*/
    def out = []
    (api.schemas ?: []).findAll { it.name ==~ pattern }.each { schema ->
        if (requirePascal) {
            if (!(schema.name ==~ pascal)) {
                out << [pointer: schema.pointer, path: schema.name,
                        message: "Schema name '${schema.name}' is a request/response object but is not PascalCase"]
            }
        } else {
            out << [pointer: schema.pointer, path: schema.name,
                    message: "Schema name '${schema.name}' is a placeholder rather than a meaningful domain name"]
        }
    }
    out
}
