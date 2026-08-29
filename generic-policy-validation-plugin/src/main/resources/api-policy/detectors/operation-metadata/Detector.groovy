{ Map api, Map rule ->
    def check = rule.parameters.check
    def out = []
    def seen = [:]
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            def loc = "${op.method} ${path.path}"
            if (check == 'unique-operation-id') {
                def oid = op.operationId
                if (oid == null || (oid instanceof String && oid.trim().isEmpty())) {
                    out << [pointer: op.pointer, path: loc, message: 'Operation has no operationId']
                } else if (seen.containsKey(oid)) {
                    out << [pointer: op.pointer, path: loc, message: "operationId '${oid}' is also used by ${seen[oid]}"]
                } else {
                    seen[oid] = loc
                }
            } else if (check == 'tags-present') {
                if ((op.tags ?: []).isEmpty()) {
                    out << [pointer: op.pointer, path: loc, message: 'Operation is not assigned any tag']
                }
            }
        }
    }
    out
}
