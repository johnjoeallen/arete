/* Finds non-standard declared headers outside the configured allow-list. */
{ Map api, Map rule ->
    def allowed = (rule.parameters?.allowed ?: '').split(',')
            .collect { it.trim().toLowerCase() }
            .findAll { it }
    def standard = [
            'accept', 'accept-charset', 'accept-encoding', 'accept-language',
            'authorization', 'cache-control', 'content-length', 'content-type',
            'cookie', 'date', 'etag', 'expect', 'from', 'host', 'if-match',
            'if-modified-since', 'if-none-match', 'if-range', 'if-unmodified-since',
            'origin', 'pragma', 'referer', 'user-agent', 'warning', 'www-authenticate',
            'location', 'retry-after', 'server', 'set-cookie', 'vary', 'www-authenticate'
    ] as Set
    def proprietary = { name ->
        def lower = name.toString().toLowerCase()
        !standard.contains(lower) && (lower.startsWith('x-') || lower.startsWith('x_'))
    }
    def diagnostics = []
    api.paths.each { path ->
        path.operationDetails.each { operation ->
            (operation.parameters ?: []).findAll { it.in == 'header' && proprietary(it.name) && !allowed.contains(it.name.toString().toLowerCase()) }.each { header ->
                diagnostics << [pointer: header.pointer, path: operation.method + ' ' + path.path,
                                 message: 'Proprietary request header is not allow-listed: ' + header.name]
            }
            (operation.responses ?: []).each { response ->
                (response.headers ?: []).findAll { proprietary(it) && !allowed.contains(it.toString().toLowerCase()) }.each { header ->
                    diagnostics << [pointer: operation.pointer, path: operation.method + ' ' + path.path,
                                     message: 'Proprietary response header is not allow-listed: ' + header]
                }
            }
        }
    }
    diagnostics
}
