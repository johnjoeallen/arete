/*
 * Bulk-operation rule. Evidence is intentionally limited to stable path,
 * method, summary, and request-body facts. A rule reports diagnostics; it
 * never scores them. Search-criteria detection is a documented heuristic for
 * paths or summaries containing filter/search/select terms.
 */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    api.paths.collectMany { path ->
        path.operationDetails.findAll { op ->
            def text = ((path.path ?: '') + ' ' + (op.summary ?: '')).toLowerCase()
            if (p['operation-type'] == 'create') {
                def createLike = text.contains('create') || text.contains('bulk')
                return createLike && (op.method != p['expected-method'] || path.path.contains('{'))
            }
            if (p['target-selection'] == 'search-criteria') {
                return op.method == p.method && (text =~ /(?i)(search|filter|criteria|query)/).find()
            }
            false
        }.collect { op ->
            [pointer: op.pointer, path: op.method + ' ' + path.path, message: p['operation-type'] == 'create' ? 'Bulk creation is not POSTed to a collection' : 'Bulk mutation uses search criteria']
        }
    }
}
