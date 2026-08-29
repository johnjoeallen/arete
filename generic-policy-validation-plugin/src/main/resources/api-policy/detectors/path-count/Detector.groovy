{ Map api, Map rule ->
    def resources = (api.paths ?: []).collect { it.path.tokenize('/').find { !it.startsWith('{') } }.findAll().toSet()
    resources.size() > rule.parameters.maximum ? [[pointer: '/paths', path: 'API', message: 'API has ' + resources.size() + ' top-level resource types (maximum ' + rule.parameters.maximum + ')']] : []
}
