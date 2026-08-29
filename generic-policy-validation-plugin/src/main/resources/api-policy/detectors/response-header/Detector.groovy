/*
 * Response-header detector
 *
 * For each response with the configured numeric status, compare documented
 * header names case-insensitively. A missing required header is one occurrence.
 * The host, not this script, decides the policy consequence.
 */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    def headers = p.headers ? p.headers.toString().split(',').collect { it.trim() }.findAll { it } : [p.header.toString()]
    api.paths.collectMany { path ->
        path.operationDetails.collectMany { operation ->
            operation.responses.findAll { response ->
                response.status.toString() == p.status.toString() &&
                    ((p.required && !headers.every { expected -> response.headers.any { header -> header.toString().equalsIgnoreCase(expected) } }) ||
                     (!p.required && headers.any { expected -> response.headers.any { header -> header.toString().equalsIgnoreCase(expected) } }))
            }.collect { response ->
                def qualifier = p.required ? 'lacks one or more required' : 'contains an unexpected'
                [pointer: operation.pointer, path: operation.method + ' ' + path.path,
                 message: 'Response ' + response.status + ' ' + qualifier + ' headers: ' + headers.join(', ')]
            }
        }
    }
}
