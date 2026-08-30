{ Map api, Map rule ->
    def out = []
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            (op.responses ?: []).each { resp ->
                (resp.headerDetails ?: []).findAll { !it.schemaPresent }.each { header ->
                    out << [pointer: op.pointer,
                            path: "${op.method} ${path.path} ${resp.status}",
                            message: "Response header '${header.name}' defines neither a schema nor content"]
                }
            }
        }
    }
    out
}
