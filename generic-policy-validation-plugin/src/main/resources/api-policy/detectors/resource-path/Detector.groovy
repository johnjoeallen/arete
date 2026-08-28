{ Map api, Map rule ->
    def verbs = ['get', 'list', 'create', 'update', 'delete', 'remove', 'add', 'set']
    def matches = { path ->
        def terminalSegment = path.path.tokenize('/').last() ?: ''
        switch (rule.parameters.match) {
            case 'operation-verb':
                return verbs.any { verb -> terminalSegment.toLowerCase().startsWith(verb) }
            case 'query-predicate':
                return terminalSegment ==~ /(?i).*(find|get|search)By[A-Z].*/
            default:
                return false
        }
    }
    def message = rule.parameters.match == 'query-predicate'
        ? 'Resource path contains a query predicate'
        : 'Resource path contains an operation verb'

    api.paths.findAll(matches).collectMany { path ->
        path.operations.collect { method ->
            [
                pointer: path.pointer + '/' + method.toLowerCase(),
                path: method + ' ' + path.path,
                message: message
            ]
        }
    }
}
