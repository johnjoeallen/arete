{ Map api, Map rule ->
    def check = rule.parameters.check
    def out = []

    if (check == 'tags-present') {
        (api.paths ?: []).each { path ->
            (path.operationDetails ?: []).each { op ->
                if ((op.tags ?: []).isEmpty()) {
                    out << [pointer: op.pointer, path: "${op.method} ${path.path}".toString(),
                            message: 'Operation is not assigned any tag']
                }
            }
        }
        return out
    }
    if (check != 'unique-operation-id') return out

    def entries = []
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            entries << [op.pointer, "${op.method} ${path.path}".toString(), op.operationId]
        }
    }

    entries.groupBy { it[2] as String }.each { key, group ->
        def head = group[0]
        boolean blank = head[2] == null || (head[2] instanceof String && head[2].trim().isEmpty())
        if (blank) {
            group.each { e -> out << [pointer: e[0], path: e[1], message: 'Operation has no operationId'] }
        } else if (group.size() > 1) {
            group.drop(1).each { e ->
                out << [pointer: e[0], path: e[1],
                        message: "operationId '${head[2]}' is also used by ${head[1]}".toString()]
            }
        }
    }
    out
}
