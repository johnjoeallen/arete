{ Map api, Map rule ->
    def connectors = ['a', 'an', 'and', 'as', 'at', 'but', 'by', 'for', 'in', 'of', 'on', 'or', 'the', 'to', 'via', 'with']
    def p = rule.parameters
    def title = api.info?.title
    def out = []
    if (!(title instanceof String) || title.trim().isEmpty()) return out
    def pointer = '/info/title'

    // Logical words: every run of non-alphanumerics is a boundary, except a
    // ' or - between two alphanumerics; leading/trailing whitespace removed.
    def words = { text ->
        ((text ?: '') as String)
            .replaceAll(/(?<![\p{L}\p{N}])['-]|['-](?![\p{L}\p{N}])|[^\p{L}\p{N}'-]/, ' ')
            .replaceAll(/ +/, ' ').trim()
            .split(' ').findAll { it && it =~ /\p{L}/ }
    }

    if (p.suffix && !title.trim().endsWith(p.suffix as String)) {
        out << [pointer: pointer, path: title, message: "API title does not end with '${p.suffix}'"]
    }

    def titleWords = words(title)
    def lastWord = titleWords ? titleWords[-1].toLowerCase() : ''
    ((p.forbidden ?: '') as String).split(',').collect { it.trim() }.findAll { it }.each { token ->
        if (token.toLowerCase() == lastWord) {
            out << [pointer: pointer, path: title, message: "API title ends with the discouraged marker '${token}'"]
        }
    }

    if (p.case == 'title-case') {
        words(title).each { word ->
            if (connectors.contains(word.toLowerCase()) || word.length() <= 3) return
            if (!(word ==~ /[A-Z0-9].*/)) {
                out << [pointer: pointer, path: title, message: "API title word '${word}' is not in Title Case"]
            }
        }
    }
    out
}
