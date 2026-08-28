/*
 * Text-style detector
 * ===================
 *
 * This trusted Groovy extension evaluates style facts about operation
 * summaries. It receives the host's stable, map-based API model and one rule
 * map. It returns occurrence maps only: score, policy disposition, and
 * presentation remain host responsibilities.
 *
 * Input contract
 * --------------
 *
 * api.paths[*].path                 is the URI template.
 * api.paths[*].operationDetails[*]  supplies method, pointer, and summary.
 *
 * `summary` is either a String or null. The host intentionally supplies
 * values rather than Swagger/OpenAPI parser objects, so detector code does
 * not couple itself to parser internals.
 *
 * Parameters
 * ----------
 *
 * initial-capital: false
 *     Match a summary that does not begin with an uppercase letter. The rule
 *     describes the undesirable condition, hence false means "capital absent"
 *     rather than a desired rendering preference.
 *
 * convention: sentence-case
 *     Match text that is not sentence case. This implementation treats a
 *     leading uppercase letter and no internal ALL-CAPS word as the safe,
 *     predictable subset of sentence case. Acronyms are deliberately not
 *     guessed; a later detector revision can add an explicit exception list.
 *
 * trailing-period: present|absent
 *     Match whether the trimmed summary ends with a full stop.
 *
 * maximum-length: integer
 *     Match a summary whose trimmed character length is greater than the
 *     supplied limit. The comparison is strictly greater: 120 is acceptable
 *     for a 120-character limit.
 *
 * match: non-action-oriented
 *     Match a summary that does not start with a conventional action verb.
 *     This is intentionally a transparent heuristic rather than an attempt
 *     to perform natural-language understanding.
 *
 * Each supplied parameter joins with AND. The descriptor and bundle loader
 * validate parameter names and types before this script is compiled or run.
 * The guards below remain defensive and make a malformed future caller fail
 * closed (no accidental fabricated occurrence).
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]
    def actionVerbs = ['Get', 'List', 'Create', 'Update', 'Delete', 'Replace', 'Search', 'Find', 'Cancel', 'Activate', 'Deactivate']

    def matches = { operation ->
        def summary = operation.summary?.trim()
        if (!summary) return false // DOC001, not this detector, reports missing documentation.

        if (parameters.containsKey('initial-capital')) {
            def hasInitialCapital = Character.isUpperCase(summary.charAt(0))
            if (hasInitialCapital != parameters['initial-capital']) return false
        }
        if (parameters.convention == 'sentence-case') {
            // Avoid false confidence: acronym handling needs an explicit policy
            // capability, so an internal all-caps word is non-conforming today.
            if (Character.isUpperCase(summary.charAt(0)) && !(summary ==~ /.*\\b[A-Z]{2,}\\b.*/)) return false
        }
        if (parameters['trailing-period'] == 'present' && !summary.endsWith('.')) return false
        if (parameters['trailing-period'] == 'absent' && summary.endsWith('.')) return false
        if (parameters.containsKey('maximum-length') && summary.length() <= parameters['maximum-length']) return false
        if (parameters.match == 'non-action-oriented' && actionVerbs.any { verb -> summary == verb || summary.startsWith(verb + ' ') }) return false
        true
    }

    def message = parameters.containsKey('initial-capital')
        ? 'Operation summary does not begin with a capital letter'
        : parameters.convention == 'sentence-case'
            ? 'Operation summary is not sentence case'
            : parameters['trailing-period'] == 'present'
                ? 'Operation summary ends with a period'
                : parameters.containsKey('maximum-length')
                    ? 'Operation summary exceeds the configured maximum length'
                    : parameters.match == 'non-action-oriented'
                        ? 'Operation summary is not action-oriented'
                        : 'Operation summary matches the configured style rule'

    api.paths.collectMany { path ->
        path.operationDetails.findAll(matches).collect { operation ->
            [pointer: operation.pointer, path: operation.method + ' ' + path.path, message: message]
        }
    }
}
