{ Map api, Map rule ->
    def check = rule.parameters.check
    def out = []
    if (check == 'inline-composition') {
        (api.schemas ?: []).each { schema ->
            if (schema.compositionKind && (schema.inlineCompositionMembers ?: 0) > 0) {
                out << [pointer: schema.pointer, path: schema.name,
                        message: "${schema.name} uses ${schema.compositionKind} with inline members instead of \$ref"]
            }
        }
    } else if (check == 'inline-body') {
        (api.paths ?: []).each { path ->
            (path.operationDetails ?: []).each { op ->
                def loc = "${op.method} ${path.path}"
                if (op.requestBodyInlineObject) {
                    out << [pointer: op.pointer, path: loc, message: 'Request body declares an inline object schema instead of a $ref']
                }
                (op.responses ?: []).findAll { it.schemaInlineObject }.each { resp ->
                    out << [pointer: op.pointer, path: "${loc} ${resp.status}",
                            message: "Response ${resp.status} declares an inline object schema instead of a \$ref"]
                }
            }
        }
    }
    out
}
