distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
        .filter { response -> rule.parameters.forbidden == "server-error"
            && parseInt(response.status, -1) >= 500
            && parseInt(response.status, -1) < 600 }
        .map { response -> diagnostic(operation.pointer,
            operation.method + " " + path.path + " " + response.status,
            "Documents a server-error (" + response.status + ") response; these should be omitted from the contract") } } };
}
