distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails
        .filter { op -> op.summary != null && op.summary.trim() != ""
            && !(
                (rule.parameters["initial-capital"] != null
                    && (op.summary.trim() ==~ /[A-Z].*/) != rule.parameters["initial-capital"])
                || (rule.parameters["convention"] == "sentence-case"
                    && op.summary.trim() ==~ /[A-Z].*/
                    && !(op.summary.trim() ==~ /.*\\b[A-Z]{2,}\\b.*/))
                || (rule.parameters["trailing-period"] == "present" && !op.summary.trim().endsWith("."))
                || (rule.parameters["trailing-period"] == "absent" && op.summary.trim().endsWith("."))
                || (rule.parameters["maximum-length"] != null
                    && op.summary.trim().length <= rule.parameters["maximum-length"])
                || (rule.parameters["minimum-words"] != null
                    && size(words(op.summary)) >= rule.parameters["minimum-words"])
                || (rule.parameters["maximum-word-length"] != null
                    && words(op.summary).all { w -> w.length <= rule.parameters["maximum-word-length"] })
                || (rule.parameters["match"] == "non-action-oriented"
                    && op.summary.trim() ==~ /(Get|List|Create|Update|Delete|Replace|Search|Find|Cancel|Activate|Deactivate)( .*)?/)) }
        .map { op -> occurrence(op.pointer, op.method + " " + path.path,
            rule.parameters["initial-capital"] != null ? "Operation summary does not begin with a capital letter"
                : rule.parameters["convention"] == "sentence-case" ? "Operation summary is not sentence case"
                : rule.parameters["trailing-period"] == "present" ? "Operation summary ends with a period"
                : rule.parameters["maximum-length"] != null ? "Operation summary exceeds the configured maximum length"
                : rule.parameters["minimum-words"] != null ? "Operation summary has too few words to be meaningful"
                : rule.parameters["maximum-word-length"] != null ? "Operation summary contains an unusually long word"
                : rule.parameters["match"] == "non-action-oriented" ? "Operation summary is not action-oriented"
                : "Operation summary matches the configured style rule") } };
}
