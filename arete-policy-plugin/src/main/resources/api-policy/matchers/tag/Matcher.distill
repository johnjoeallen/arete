distill(api, rule) {
    return rule.parameters["check"] == "documented"
        ? api.tags
            .filter { t -> t.description is blank }
            .map { t -> occurrence(t.pointer, t.name,
                "Tag '" + t.name + "' has no description") }
    : rule.parameters["check"] == "unique"
        ? api.tags
            .group { t -> t.name }
            .values
            .filter { dupes -> count(dupes) > 1 }
            .expand { dupes -> enumerate(dupes)
                .filter { indexed -> indexed[0] > 0 }
                .map { indexed -> occurrence(indexed[1].pointer, indexed[1].name,
                    "Tag '" + dupes[0].name + "' is declared more than once") } }
    : rule.parameters["check"] == "declared"
        ? distinct(api.paths.expand { p -> p.operationDetails.expand { op -> op.tags } })
            .filter { name -> !api.tags.any { t -> t.name == name } }
            .map { name -> occurrence("/tags", name,
                "Operation tag '" + name + "' is not declared in the top-level tags list") }
    : rule.parameters["check"] == "name-convention"
        ? distinct(api.paths.expand { p -> p.operationDetails.expand { op -> op.tags } })
            .filter { name -> !(
                rule.parameters["convention"] == "camelCase" ? name ==~ /[a-z][A-Za-z0-9]*/
                : rule.parameters["convention"] == "snake_case" ? name ==~ /[a-z][a-z0-9]*(?:_[a-z0-9]+)*/
                : rule.parameters["convention"] == "kebab-case" ? name ==~ /[a-z][a-z0-9]*(?:-[a-z0-9]+)*/
                : rule.parameters["convention"] == "hyphenated" ? name ==~ /[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+/
                : true) }
            .map { name -> occurrence("/tags", name,
                "Tag name '" + name + "' does not follow the "
                    + rule.parameters["convention"] + " convention") }
    : [];
}
