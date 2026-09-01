distill(api, rule) {
    return api.descriptions
        .filter { field ->
            regexSearch("(?i)<\\s*/?\\s*script\\b", field.text)
            || regexSearch("(?i)javascript:", field.text)
            || regexSearch("(?i)\\bon(?:load|error|click|mouseover|focus|submit)\\s*=", field.text)
            || regexSearch("(?i)\\beval\\s*\\(", field.text) }
        .map { field -> occurrence(field.pointer, "document",
            "Description or summary contains active markup "
                + "(a script tag, javascript: URL, event handler, or eval call)") };
}
