{ Map api, Map rule ->
    def forbidden = rule.parameters.forbidden
    def out = []
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            (op.responses ?: []).each { resp ->
                def code = (resp.status as String).isInteger() ? (resp.status as String).toInteger() : -1
                if (forbidden == 'server-error' && code >= 500 && code < 600) {
                    out << [pointer: op.pointer,
                            path: "${op.method} ${path.path} ${resp.status}",
                            message: "Documents a server-error (${resp.status}) response; these should be omitted from the contract"]
                }
            }
        }
    }
    out
}
