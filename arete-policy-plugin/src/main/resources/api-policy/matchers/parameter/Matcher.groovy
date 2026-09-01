{ Map api, Map rule ->
    def p = rule.parameters
    def check = p.check
    def maximum = p.maximum
    def out = []
    (api.paths ?: []).each { path ->
        def template = path.templateParameters ?: []
        (path.operationDetails ?: []).each { op ->
            def params = op.parameters ?: []
            def loc = "${op.method} ${path.path}"
            if (check == 'max-count') {
                if (maximum != null && params.size() > maximum) {
                    out << [pointer: op.pointer, path: loc,
                            message: "Operation declares ${params.size()} parameters, more than the maximum of ${maximum}"]
                }
            } else if (check == 'path-required') {
                params.findAll { it.in == 'path' && !it.required }.each { prm ->
                    out << [pointer: prm.pointer, path: "${loc} ${prm.name}",
                            message: "Path parameter '${prm.name}' is not marked required"]
                }
            } else if (check == 'template-match') {
                def declared = params.findAll { it.in == 'path' }.collect { it.name }
                declared.findAll { !template.contains(it) }.each { name ->
                    out << [pointer: op.pointer, path: loc,
                            message: "Path parameter '${name}' has no matching {placeholder} in the path template"]
                }
                template.findAll { !declared.contains(it) }.each { name ->
                    out << [pointer: op.pointer, path: loc,
                            message: "Path template placeholder '{${name}}' has no matching path parameter"]
                }
            } else if (check == 'schema-present') {
                params.findAll { !it.schemaPresent }.each { prm ->
                    out << [pointer: prm.pointer, path: "${loc} ${prm.name}",
                            message: "Parameter '${prm.name}' defines neither a schema nor content"]
                }
            }
        }
    }
    out
}
