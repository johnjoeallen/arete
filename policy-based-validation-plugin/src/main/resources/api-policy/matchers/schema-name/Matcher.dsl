distill(api, rule) {
    return api.schemas
        .filter { schema -> schema.name ==~ rule.parameters.pattern }
        .filter { schema -> rule.parameters.case != "pascal-case"
            || !(schema.name ==~ /[A-Z][A-Za-z0-9]*/) }
        .map { schema -> occurrence(schema.pointer, schema.name,
            rule.parameters.case == "pascal-case"
                ? "Schema name '" + schema.name + "' is a request/response object but is not PascalCase"
                : "Schema name '" + schema.name + "' is a placeholder rather than a meaningful domain name") };
}
