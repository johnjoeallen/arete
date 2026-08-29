{ Map api, Map rule ->
    def connectors = ['a', 'an', 'and', 'as', 'at', 'but', 'by', 'for', 'in', 'of', 'on', 'or', 'the', 'to', 'via', 'with']
    def p = rule.parameters
    def title = api.info?.title
    def out = []
    if (!(title instanceof String) || title.trim().isEmpty()) return out
    def pointer = '/info/title'

    if (p.suffix && !title.trim().endsWith(p.suffix as String)) {
        out << [pointer: pointer, path: title, message: "API title does not end with '${p.suffix}'"]
    }

    def words = title.split(' ').findAll { it }
    def lastWord = words ? words[-1].replaceAll(/[()\[\]:,.]/, '').toLowerCase() : ''
    ((p.forbidden ?: '') as String).split(',').collect { it.trim() }.findAll { it }.each { token ->
        if (token.toLowerCase() == lastWord) {
            out << [pointer: pointer, path: title, message: "API title ends with the discouraged marker '${token}'"]
        }
    }

    if (p.case == 'title-case') {
        title.split(' ').findAll { it }.each { word ->
            def core = word.replaceAll(/[()\[\]:,.]/, '')
            if (core.isEmpty() || connectors.contains(core.toLowerCase()) || core.length() <= 3) return
            if (!(core ==~ /[A-Z0-9].*/)) {
                out << [pointer: pointer, path: title, message: "API title word '${word}' is not in Title Case"]
            }
        }
    }
    out
}
