distill(api, rule) {
    return rule.parameters["required"] == "identifier"
        ? (type(api.info["apiId"]) != "string" || api.info["apiId"] is blank
            ? [occurrence("/", "API", "API metadata is missing x-api-id")]
            : [])
        : rule.parameters["required"] == "audience"
        ? (type(api.info["audience"]) != "string" || api.info["audience"] is blank
            ? [occurrence("/", "API", "API metadata is missing x-audience")]
            : [])
        : [["title", "title"], ["description", "description"],
           ["contactName", "contact name"], ["contactEmail", "contact email"]]
              .filter { pair -> type(api.info[pair[0]]) != "string" || api.info[pair[0]] is blank }
              .map { pair -> occurrence("/info", "API", "API metadata is missing " + pair[1]) }
          + (type(api.info["version"]) != "string"
                || !(api.info["version"] ==~ /0|[1-9][0-9]*\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?/)
              ? [occurrence("/info", "API", "API metadata is missing semantic version")]
              : []);
}
