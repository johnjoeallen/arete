distill(api, rule) {
    return checks(api.operations.filter { !(it.summary is blank) }) {

        filter { rule.parameters["initial-capital"] != null
                 && (it.summary.trim() ==~ /[A-Z].*/) == rule.parameters["initial-capital"] }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary does not begin with a capital letter") },

        filter { rule.parameters["convention"] == "sentence-case"
                 && !(it.summary.trim() ==~ /[A-Z].*/
                      && !(it.summary.trim() ==~ /.*\\b[A-Z]{2,}\\b.*/)) }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary is not sentence case") },

        filter { rule.parameters["trailing-period"] == "present" && it.summary.trim().endsWith(".") }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary ends with a period") },

        filter { rule.parameters["maximum-length"] != null
                 && it.summary.trim().length > rule.parameters["maximum-length"] }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary exceeds the configured maximum length") },

        filter { rule.parameters["minimum-words"] != null
                 && count(words(it.summary)) < rule.parameters["minimum-words"] }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary has too few words to be meaningful") },

        filter { rule.parameters["maximum-word-length"] != null
                 && words(it.summary).any { w -> w.length > rule.parameters["maximum-word-length"] } }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary contains an unusually long word") },

        filter { rule.parameters["match"] == "non-action-oriented"
                 && !it.summary.trim().startsWithWord(rule.parameters["action-prefixes"]) }
          .map { occurrence(it.pointer, it.method + " " + it.path,
                            "Operation summary is not action-oriented") }
    };
}
