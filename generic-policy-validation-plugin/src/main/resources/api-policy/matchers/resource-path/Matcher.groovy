/*
 * Resource-path rule
 * ======================
 *
 * This file evaluates to the closure called by Areté's policy runtime.
 * A rule must return diagnostics only: it does not select a policy,
 * assign a severity, or calculate any score. The host applies the active
 * policy's deduction once for the matching rule, regardless of how many
 * diagnostics this closure returns.
 *
 * Input contract
 * --------------
 *
 * `api` is a deliberately small, stable map rather than a parser-owned
 * OpenAPI object. This rule currently uses:
 *
 *   api.paths: List<Map>
 *     path.path:       resource path, e.g. "/customers/{customerId}"
 *     path.pointer:    escaped JSON Pointer, e.g. "/paths/~1customers"
 *     path.operations: HTTP method names, e.g. ["GET", "POST"]
 *
 * `rule` is the one declarative rule being evaluated. Its relevant fields
 * are `id`, `scope`, and `parameters`. Do not expect the active policy to
 * be present here: policies are intentionally invisible to rule code.
 *
 * Supported rule parameter
 * ------------------------
 *
 *   parameters.match: "operation-verb" | "query-predicate" | "rpc-style"
 *                     | "custom-action" | "action-style"
 *
 * The descriptor in Matcher.md is the authoritative validation schema.
 * The switch's default is deliberately a no-match as a defensive backstop;
 * an invalid parameter value is rejected during bundle loading before this
 * rule is invoked.
 *
 * Output contract
 * ---------------
 *
 * Each map returned below is one reportable diagnostic:
 *
 *   pointer: a JSON Pointer rooted at one operation
 *   path:    "<METHOD> <path>" for endpoint attribution in the Viewer
 *   message: a concise explanation shown with the rule diagnostic
 *
 * An diagnostic is emitted for every affected operation rather than just
 * every matching path. That lets Areté attach the finding to the
 * relevant API element in its endpoint view. The host still charges the
 * rule's configured deduction only once.
 */
{ Map api, Map rule ->
    if (rule.parameters?.match == 'trailing-slash') return api.paths.findAll { it.path.size() > 1 && it.path.endsWith('/') }.collect { [pointer: it.pointer, path: it.path, message: 'Resource path has an unnecessary trailing slash'] }
    if (rule.parameters?.match == 'embedded-identifier') return api.paths.findAll { it.path.tokenize('/').any { segment -> segment ==~ /.*(?:Id|ID|[0-9]{2,}).*/ && !segment.startsWith('{') } }.collect { [pointer: it.pointer, path: it.path, message: 'Resource identifier is embedded in a path segment'] }
    def verbs = ['get', 'list', 'create', 'update', 'delete', 'remove', 'add', 'set']
    def matches = { path ->
        def terminalSegment = path.path.tokenize('/').last() ?: ''
        switch (rule.parameters.match) {
            case 'operation-verb':
                // e.g. /getCustomers, /createCustomer, /deleteCustomer/{id}
                return verbs.any { verb -> terminalSegment.toLowerCase().startsWith(verb) }
            case 'query-predicate':
                // e.g. /pet/findByStatus. This is available for a future
                // catalogue rule even though the bundled Strict policy
                // currently contains REST001 only.
                return terminalSegment ==~ /(?i).*(find|get|search)By[A-Z].*/
            case 'rpc-style':
                // A verb-like terminal segment beneath another resource is a
                // transparent first-pass signal of an RPC-shaped endpoint:
                // /customer/get or /customer/update.
                return path.path.tokenize('/').size() > 1 && verbs.contains(terminalSegment.toLowerCase())
            case 'custom-action':
                // Explicit /actions/{verb} collections encode an action as a
                // sub-path rather than a resource representation.
                return path.path ==~ /(?i).*\/actions(?:\/[^\/]+)?/
            case 'action-style':
                return path.path ==~ /(?i).*\/actions(?:\/[^\/]+)?/ || verbs.any { verb -> terminalSegment.toLowerCase().startsWith(verb) }
            default:
                return false
        }
    }
    def message = switch (rule.parameters.match) {
        case 'query-predicate' -> 'Resource path contains a query predicate'
        case 'rpc-style' -> 'API uses RPC-style resource design'
        case 'custom-action' -> 'Custom action resource is used'
        case 'action-style' -> 'Action-style endpoint is used'
        default -> 'Resource path contains an operation verb'
    }

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
