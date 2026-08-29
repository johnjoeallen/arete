{ Map api, Map rule ->
    def out = []
    if (rule.parameters.check != 'unique-error-payloads') return out
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            def seen = [:]
            (op.responses ?: []).each { resp ->
                def code = (resp.status as String).isInteger() ? (resp.status as String).toInteger() : -1
                if (code >= 400 && code < 600) {
                    (resp.exampleStrings ?: []).each { example ->
                        if (seen.containsKey(example)) {
                            out << [pointer: op.pointer, path: "${op.method} ${path.path}",
                                    message: "Error responses ${seen[example]} and ${resp.status} share an identical example payload"]
                        } else {
                            seen[example] = resp.status as String
                        }
                    }
                }
            }
        }
    }
    out
}
