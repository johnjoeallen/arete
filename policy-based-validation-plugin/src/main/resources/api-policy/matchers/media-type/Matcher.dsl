distill(api, rule) {
    return rule.parameters["location"] == "request"
        ? checks(api.operations) {

            filter { o -> rule.parameters["match"] == "absent" && count(o.requestMediaTypes) == 0 }
              .map { o -> occurrence(o.pointer, o.method + " " + o.path,
                                     "Request body media type absent") },

            filter { o -> rule.parameters["match"] == "wildcard"
                          && o.requestMediaTypes.any { t -> t == "*/*" || t.endsWith("/*") || t.contains("*") } }
              .map { o -> occurrence(o.pointer, o.method + " " + o.path,
                                     "Request body media type wildcard") },

            filter { o -> rule.parameters["match"] == "not-allowed"
                          && o.requestMediaTypes.any { t ->
                                 !(tokenize(",", rule.parameters["allowed"]).any { a -> a.trim().lower() == t.lower() }) } }
              .map { o -> occurrence(o.pointer, o.method + " " + o.path,
                                     "Request body media type not-allowed") }
          }
        : checks(api.responses) {

            filter { r -> rule.parameters["match"] == "absent" && count(r.mediaTypes) == 0 }
              .map { r -> occurrence(r.pointer, r.method + " " + r.path,
                                     "Response " + r.status + " media type absent") },

            filter { r -> rule.parameters["match"] == "wildcard"
                          && r.mediaTypes.any { t -> t == "*/*" || t.endsWith("/*") || t.contains("*") } }
              .map { r -> occurrence(r.pointer, r.method + " " + r.path,
                                     "Response " + r.status + " media type wildcard") },

            filter { r -> rule.parameters["match"] == "not-allowed"
                          && r.mediaTypes.any { t ->
                                 !(tokenize(",", rule.parameters["allowed"]).any { a -> a.trim().lower() == t.lower() }) } }
              .map { r -> occurrence(r.pointer, r.method + " " + r.path,
                                     "Response " + r.status + " media type not-allowed") }
          };
}
