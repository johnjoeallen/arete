/*
 * Response-code detector
 * ======================
 *
 * Stable input: each operation contains `responses[*]` with `status`,
 * `description`, and `headers`. No parser classes are exposed.
 *
 * Operation checks report one occurrence per operation; response checks report
 * one per matching response. Required status checks are absence checks, while
 * an explicit `status` selects a response. This detector reports documented
 * contract facts only; it cannot prove what a running server returns.
 */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    def statusNumber = { value ->
        try { Integer.parseInt(value.toString()) } catch (Exception ignored) { -1 }
    }
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
