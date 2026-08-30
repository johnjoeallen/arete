/* Checks documented request and response media types. */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    def allowed = (p.allowed ?: '').toString().split(',').collect { it.trim().toLowerCase() }.findAll { it }
    def wildcard = { type -> type == '*/*' || type.endsWith('/*') || type.contains('*') }
    def matches = { types ->
        if (p.match == 'absent') return types.isEmpty()
        if (p.match == 'wildcard') return types.any { wildcard(it.toString()) }
        if (p.match == 'not-allowed') return types.any { !allowed.contains(it.toString().toLowerCase()) }
        false
    }
    api.paths.collectMany { path ->
        path.operationDetails.collectMany { operation ->
            if (p.location == 'request') {
                def types = operation.requestMediaTypes ?: []
                return matches(types) ? [[pointer: operation.pointer, path: operation.method + ' ' + path.path, message: 'Request body media type ' + p.match]] : []
            }
            operation.responses.findAll { response -> matches(response.mediaTypes ?: []) }.collect { response ->
                [pointer: operation.pointer, path: operation.method + ' ' + path.path, message: 'Response ' + response.status + ' media type ' + p.match]
            }
        }
    }
}
