/*
 * Response-header detector
 *
 * For each response with the configured numeric status, compare documented
 * header names case-insensitively. A missing required header is one occurrence.
 * The host, not this script, decides the policy consequence.
 */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    api.paths.collectMany { path ->
        path.operationDetails.collectMany { operation ->
            operation.responses.findAll { response ->
                response.status.toString() == p.status.toString() &&
                    p.required && !response.headers.any { header -> header.toString().equalsIgnoreCase(p.header.toString()) }
            }.collect { response ->
                def qualifier = p.required ? 'lacks' : 'contains an unexpected'
                [pointer: operation.pointer, path: operation.method + ' ' + path.path,
                 message: 'Response ' + response.status + ' ' + qualifier + ' ' + p.header + ' header']
            }
        }
    }
}
