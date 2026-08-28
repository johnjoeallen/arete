{ Map api, Map rule ->
    def verbs = ['get', 'list', 'create', 'update', 'delete', 'remove', 'add', 'set']

    api.paths.findAll { path ->
        def terminalSegment = path.path.tokenize('/').last()?.toLowerCase() ?: ''
        verbs.any { verb -> terminalSegment.startsWith(verb) }
    }.collect { path ->
        [
            pointer: path.pointer,
            path: path.path,
            message: 'Resource path contains an operation verb'
        ]
    }
}
