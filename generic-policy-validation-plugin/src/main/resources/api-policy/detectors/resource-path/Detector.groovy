/*
 * Resource-path detector
 * ======================
 *
 * This file evaluates to the closure called by Speculate's policy runtime.
 * A detector must return occurrences only: it does not select a policy,
 * assign a severity, or calculate any score. The host applies the active
 * policy's deduction once for the matching rule, regardless of how many
 * occurrences this closure returns.
 *
 * Input contract
 * --------------
 *
 * `api` is a deliberately small, stable map rather than a parser-owned
 * OpenAPI object. This detector currently uses:
 *
 *   api.paths: List<Map>
 *     path.path:       resource path, e.g. "/customers/{customerId}"
 *     path.pointer:    escaped JSON Pointer, e.g. "/paths/~1customers"
 *     path.operations: HTTP method names, e.g. ["GET", "POST"]
 *
 * `rule` is the one declarative rule being evaluated. Its relevant fields
 * are `id`, `scope`, and `parameters`. Do not expect the active policy to
 * be present here: policies are intentionally invisible to detector code.
 *
 * Supported rule parameter
 * ------------------------
 *
 *   parameters.match: "operation-verb" | "query-predicate"
 *
 * The descriptor in Detector.md is the authoritative validation schema.
 * The switch's default is deliberately a no-match as a defensive backstop;
 * an invalid parameter value is rejected during bundle loading before this
 * detector is invoked.
 *
 * Output contract
 * ---------------
 *
 * Each map returned below is one reportable occurrence:
 *
 *   pointer: a JSON Pointer rooted at one operation
 *   path:    "<METHOD> <path>" for endpoint attribution in the Viewer
 *   message: a concise explanation shown with the rule violation
 *
 * An occurrence is emitted for every affected operation rather than just
 * every matching path. That lets Speculate attach the finding to the
 * relevant API element in its endpoint view. The host still charges the
 * rule's configured deduction only once.
 */
{ Map api, Map rule ->
    def verbs = ['get', 'list', 'create', 'update', 'delete', 'remove', 'add', 'set']
    def matches = { path ->
        def terminalSegment = path.path.tokenize('/').last() ?: ''
        switch (rule.parameters.match) {
            case 'operation-verb':
                // e.g. /getCustomers, /createCustomer, /deleteCustomer/{id}
                return verbs.any { verb -> terminalSegment.toLowerCase().startsWith(verb) }
            case 'query-predicate':
                // e.g. /pet/findByStatus. This is available for a future
                // catalogue rule even though the bundled Starter policy
                // currently contains REST001 only.
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
                // A path-level pointer would be a general finding. Add the
                // method segment so the Viewer can associate it with an API
                // operation and display it alongside that operation.
                pointer: path.pointer + '/' + method.toLowerCase(),
                path: method + ' ' + path.path,
                message: message
            ]
        }
    }
}
