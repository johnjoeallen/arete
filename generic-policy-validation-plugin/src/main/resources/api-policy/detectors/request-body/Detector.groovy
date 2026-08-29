{ Map api, Map rule ->
    def p = rule.parameters
    def check = p.check
    def methods = ((p.methods ?: '') as String).split(',').collect { it.trim().toUpperCase() }.findAll { it }
    def out = []
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            def loc = "${op.method} ${path.path}"
            if (check == 'forbidden-on-methods') {
                if (methods.contains(op.method) && op.requestBodyPresent) {
                    out << [pointer: op.pointer, path: loc, message: "${op.method} operation declares a request body"]
                }
            } else if (check == 'required-flag-missing') {
                if (op.requestBodyPresent && !op.requestBodyRequired) {
                    out << [pointer: op.pointer, path: loc, message: 'Request body is present but not marked required: true']
                }
            }
        }
    }
    out
}
