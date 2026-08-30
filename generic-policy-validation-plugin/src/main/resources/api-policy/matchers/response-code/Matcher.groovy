/*
 * Response-code rule
 * ======================
 *
 * Stable input: each operation contains `responses[*]` with `status`,
 * `description`, and `headers`. No parser classes are exposed.
 *
 * Operation checks report one diagnostic per operation; response checks report
 * one per matching response. Required status checks are absence checks, while
 * an explicit `status` selects a response. This rule reports documented
 * contract facts only; it cannot prove what a running server returns.
 */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    def statusNumber = { value ->
        try { Integer.parseInt(value.toString()) } catch (Exception ignored) { -1 }
    }
    if (p['response-shape'] == 'json-object') return api.paths.collectMany { path -> path.operationDetails.collectMany { op -> op.responses.findAll { statusNumber(it.status) >= 200 && statusNumber(it.status) < 300 && it.schemaTypes && it.schemaTypes.any { t -> t != 'object' } }.collect { [pointer: path.pointer, path: op.method + ' ' + path.path, message: 'Successful response is not a JSON object'] } } }
    if (p['error-format'] == 'problem-json') return api.paths.collectMany { path -> path.operationDetails.collectMany { op -> op.responses.findAll { statusNumber(it.status) >= 400 && statusNumber(it.status) < 600 && !(op.mediaTypes ?: []).contains('application/problem+json') }.collect { [pointer: path.pointer, path: op.method + ' ' + path.path, message: 'Error response does not declare application/problem+json'] } } }
    def operationMatches = { path, operation ->
        if (p['operation-type'] == 'create' && !(operation.method == 'POST' || operation.method == 'PUT')) return false
        if (p['operation-type'] == 'identifiable-resource-retrieval' && !(operation.method == 'GET' && path.path.contains('{'))) return false
        if (p['required-status'] != null && !operation.responses.any { statusNumber(it.status) == p['required-status'] }) return true
        false
    }
    def responseMatches = { response ->
        if (p.status != null && statusNumber(response.status) != p.status) return false
        if (p['match'] == 'semantic-conflict') {
            def code = statusNumber(response.status)
            return code >= 200 && code < 300 && (response.description ?: '') ==~ /(?i).*\\b(error|failure|failed|invalid)\\b.*/
        }
        p.status != null
    }
    api.paths.collectMany { path ->
        path.operationDetails.collectMany { operation ->
            if (rule.scope == 'operation') {
                return operationMatches(path, operation) ? [[pointer: operation.pointer, path: operation.method + ' ' + path.path, message: 'Operation lacks the required documented status']] : []
            }
            operation.responses.findAll(responseMatches).collect { response ->
                [pointer: operation.pointer, path: operation.method + ' ' + path.path, message: p.match == 'semantic-conflict' ? 'Status code conflicts with response semantics' : 'Response uses the configured status code']
            }
        }
    }
}
