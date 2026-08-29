/*
 * Operation-semantics detector
 * ============================
 *
 * This detector works solely against the stable detector map:
 *
 *   api.paths[*].path
 *   api.paths[*].operationDetails[*].method
 *   api.paths[*].operationDetails[*].summary
 *   api.paths[*].operationDetails[*].pointer
 *
 * It deliberately receives no implementation objects and makes no network
 * calls. An OpenAPI contract cannot establish whether a GET genuinely changes
 * server state, so all checks below are deliberately conservative textual
 * signals. The rule documentation and resulting message say "appears" or
 * "may" where this boundary matters.
 *
 * Match modes:
 *
 * safe + GET
 *     A GET whose URI or summary contains a mutation verb.
 * full-resource-replacement + POST
 *     A POST on an identified resource whose documentation says replace.
 * partial-update + PUT
 *     A PUT whose documentation says partial/update/patch.
 * inconsistent-method-resource-semantics
 *     Any operation whose documentation signals a mutation under GET, or a
 *     replacement under POST. This is intentionally a union of the focused
 *     checks, suitable for an advisory umbrella rule.
 * unsupported-operation-semantics-unclear
 *     A non-standard HTTP method cannot be represented by the host's OpenAPI
 *     method map today; for standard methods this first pass reports nothing.
 *     Retaining this explicit no-match is safer than inventing a violation.
 *
 * The detector returns occurrences only. The host policy applies a deduction
 * at most once per rule, regardless of the number of flagged operations.
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]
    def mutation = /(?i)\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b/
    def replacement = /(?i)\b(replace|replacement)\b/
    def partial = /(?i)\b(partial|patch|update)\b/

    def matches = { path, operation ->
        if (parameters.method && operation.method != parameters.method) return false
        def text = ((path.path ?: '') + ' ' + (operation.summary ?: '')).trim()
        def getMutation = operation.method == 'GET' && text ==~ /.*${mutation}.*/
        def postReplacement = operation.method == 'POST' && path.path ==~ /.+\/\{[^}]+\}.*/ && text ==~ /.*${replacement}.*/
        def putPartial = operation.method == 'PUT' && text ==~ /.*${partial}.*/

        if (parameters.expected == 'safe') return getMutation
        switch (parameters.match) {
            case 'full-resource-replacement': return postReplacement
            case 'partial-update': return putPartial
            case 'inconsistent-method-resource-semantics': return getMutation || postReplacement
            case 'unsupported-operation-semantics-unclear': return false
            default: return false
        }
    }

    def message = parameters.expected == 'safe' ? 'GET operation appears to mutate state'
        : parameters.match == 'full-resource-replacement' ? 'POST appears to replace an identified resource'
        : parameters.match == 'partial-update' ? 'PUT appears to perform a partial update'
        : parameters.match == 'inconsistent-method-resource-semantics' ? 'HTTP method and resource semantics appear inconsistent'
        : 'Supported operation semantics are unclear'

    api.paths.collectMany { path ->
        path.operationDetails.findAll { operation -> matches(path, operation) }.collect { operation ->
            [pointer: operation.pointer, path: operation.method + ' ' + path.path, message: message]
        }
    }
}
