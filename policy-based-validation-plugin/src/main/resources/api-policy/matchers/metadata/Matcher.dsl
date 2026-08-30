distill(api, rule) {
    return rule.parameters["required"] == "identifier"
        ? (type(api.info["apiId"]) != "string" || api.info["apiId"] is blank
            ? [occurrence("/", "API", "API metadata is missing x-api-id")]
            : [])
        : rule.parameters["required"] == "audience"
        ? (type(api.info["audience"]) != "string" || api.info["audience"] is blank
            ? [occurrence("/", "API", "API metadata is missing x-audience")]
            : [])
        : rule.parameters["required"] == "license"
        ? ((type(api.info["licenseName"]) != "string" || api.info["licenseName"] is blank)
            ? [occurrence("/info/license", "API", "API metadata does not declare a license")]
            : (type(api.info["licenseUrl"]) != "string" || api.info["licenseUrl"] is blank)
                ? [occurrence("/info/license", "API", "API license does not declare a url")]
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
