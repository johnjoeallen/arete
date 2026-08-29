def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    if check != "unique-error-payloads":
        return out
    for path in api["paths"]:
        for op in path["operationDetails"]:
            seen = {}
            for resp in op["responses"]:
                code = parse_int(str(resp["status"]), -1)
                if code < 400 or code >= 600:
                    continue
                for example in resp.get("exampleStrings", []):
                    if example in seen:
                        out.append({
                            "pointer": op["pointer"],
                            "path": op["method"] + " " + path["path"],
                            "message": "Error responses " + seen[example] + " and " + str(resp["status"]) + " share an identical example payload",
                        })
                    else:
                        seen[example] = str(resp["status"])
    return out
