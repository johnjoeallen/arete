def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    if check == "inline-composition":
        for schema in api["schemas"]:
            if schema.get("compositionKind") and schema.get("inlineCompositionMembers", 0) > 0:
                out.append({
                    "pointer": schema["pointer"],
                    "path": schema["name"],
                    "message": schema["name"] + " uses " + schema["compositionKind"] + " with inline members instead of $ref",
                })
    elif check == "inline-body":
        for path in api["paths"]:
            for op in path["operationDetails"]:
                loc = op["method"] + " " + path["path"]
                if op.get("requestBodyInlineObject"):
                    out.append({"pointer": op["pointer"], "path": loc, "message": "Request body declares an inline object schema instead of a $ref"})
                for resp in op["responses"]:
                    if resp.get("schemaInlineObject"):
                        out.append({
                            "pointer": op["pointer"],
                            "path": loc + " " + str(resp["status"]),
                            "message": "Response " + str(resp["status"]) + " declares an inline object schema instead of a $ref",
                        })
    return out
