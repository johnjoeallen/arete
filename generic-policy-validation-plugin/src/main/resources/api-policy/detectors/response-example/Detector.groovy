{ Map api, Map rule ->
    def out = []
    if (rule.parameters.check != 'unique-error-payloads') return out

    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            def pairs = []
            (op.responses ?: []).each { resp ->
                def code = (resp.status as String).isInteger() ? (resp.status as String).toInteger() : -1
                if (code >= 400 && code < 600) {
                    (resp.exampleStrings ?: []).each { example ->
                        pairs << [(resp.status as String), example]
                    }
                }
            }

            pairs.groupBy { it[1] }.each { key, group ->
                if (group.size() > 1) {
                    group.drop(1).each { pair ->
                        out << [pointer: op.pointer, path: "${op.method} ${path.path}".toString(),
                                message: ("Error responses ${group[0][0]} and ${pair[0]} " +
                                        "share an identical example payload").toString()]
                    }
                }
            }
        }
    }
    out
}
