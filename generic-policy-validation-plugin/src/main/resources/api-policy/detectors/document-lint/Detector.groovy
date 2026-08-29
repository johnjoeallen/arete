{ Map api, Map rule ->
    def p = rule.parameters
    def check = p.check
    def lint = api.lint ?: [:]
    def out = []
    if (check == 'parser-message') {
        if (!p.pattern) return out
        def pattern = ~(p.pattern as String)
        (lint.parserMessages ?: []).findAll { it =~ pattern }.each { message ->
            out << [pointer: '/', path: 'document', message: "Parser reported: ${message}"]
        }
    } else if (check == 'numeric-status-key') {
        def codes = lint.numericStatusKeys ?: []
        if (codes) {
            out << [pointer: '/paths', path: 'document',
                    message: "HTTP status keys are declared as bare numbers, not strings: ${codes.join(', ')}"]
        }
    }
    out
}
