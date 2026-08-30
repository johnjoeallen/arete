/*
 * Operation rule
 * ==================
 *
 * This script evaluates to the closure called by the policy runtime. It
 * accepts exactly two maps: `api`, the stable read-only model published by
 * the host, and `rule`, the one declarative rule under evaluation. It must
 * return a collection of diagnostic maps; it must not calculate a score,
 * choose a severity, inspect the active policy, or modify the API model.
 *
 * Stable input model used here
 * ----------------------------
 *
 *   api.paths: List<Map>
 *     path.path:             e.g. "/customers/{customer_id}"
 *     path.operationDetails: List<Map>
 *       operation.method:             upper-case HTTP method
 *       operation.pointer:            operation JSON Pointer
 *       operation.summary:            summary text, or null
 *       operation.requestBodyPresent: boolean
 *
 * Only depend on fields documented by this rule or the host's stable
 * rule API. Parser-owned Swagger/OpenAPI Java objects are never exposed
 * to scripts, allowing the host parser implementation to change safely.
 *
 * Rule configuration
 * ------------------
 *
 * All parameters are optional and combine using logical AND:
 *
 *   method: GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS
 *   summary: present|absent
 *   request-body: present|absent
 *
 * The companion Matcher.md declares these values and the bundle loader
 * validates them before the script runs. The local default below is still
 * defensive so a future host cannot accidentally turn invalid input into a
 * spurious match.
 *
 * Output model
 * ------------
 *
 * Each matching operation becomes one diagnostic map. `pointer` and `path`
 * let Areté attach the finding to the endpoint in the Viewer. Multiple
 * matching operations remain separately visible for remediation; the host
 * applies the policy's deduction once for the rule as a whole.
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]

    def matches = { operation ->
        if (parameters.method && operation.method != parameters.method) return false
        if (parameters.summary == 'present' && !operation.summary?.trim()) return false
        if (parameters.summary == 'absent' && operation.summary?.trim()) return false
        if (parameters.description == 'present' && !operation.description?.trim()) return false
        if (parameters.description == 'absent' && operation.description?.trim()) return false
        if (parameters['request-body'] == 'present' && !operation.requestBodyPresent) return false
        if (parameters['request-body'] == 'absent' && operation.requestBodyPresent) return false
        return true
    }

    def message = parameters.summary == 'absent'
        ? 'Operation summary is missing'
        : parameters.description == 'absent'
        ? 'Operation description is missing'
        : parameters['request-body'] == 'present'
            ? 'Operation has a request body'
            : parameters['request-body'] == 'absent'
                ? 'Operation has no request body'
                : parameters.method
                    ? parameters.method + ' operation is used'
                    : 'Operation matches the configured rule'

    api.paths.collectMany { path ->
        path.operationDetails.findAll(matches).collect { operation ->
            [
                pointer: operation.pointer,
                path: operation.method + ' ' + path.path,
                message: message
            ]
        }
    }
}
