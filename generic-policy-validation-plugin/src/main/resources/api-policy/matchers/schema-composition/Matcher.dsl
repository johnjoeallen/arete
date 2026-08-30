distill(api, rule) {
    return rule.parameters.check == "inline-composition"
        ? api.schemas
            .filter { schema -> schema.compositionKind && schema.inlineCompositionMembers > 0 }
            .map { schema -> diagnostic(schema.pointer, schema.name,
                schema.name + " uses " + schema.compositionKind + " with inline members instead of $ref") }
        : api.paths.expand { path -> path.operationDetails.expand { operation ->
            (operation.requestBodyInlineObject
                ? tokenize(",", "x").map { u -> diagnostic(operation.pointer,
                    operation.method + " " + path.path,
                    "Request body declares an inline object schema instead of a $ref") }
                : tokenize(",", "x").filter { u -> false })
            + operation.responses
                .filter { response -> response.schemaInlineObject }
                .map { response -> diagnostic(operation.pointer,
                    operation.method + " " + path.path + " " + response.status,
                    "Response " + response.status + " declares an inline object schema instead of a $ref") } } };
}
