def detect(api, rule):
    p = rule["parameters"]
    check = p["check"]
    methods = [token.strip().upper() for token in str(p.get("methods", "")).split(",") if token.strip()]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            loc = op["method"] + " " + path["path"]
            if check == "forbidden-on-methods":
                if op["method"] in methods and op["requestBodyPresent"]:
                    out.append({
                        "pointer": op["pointer"],
                        "path": loc,
                        "message": op["method"] + " operation declares a request body",
                    })
            elif check == "required-flag-missing":
                if op["requestBodyPresent"] and not op["requestBodyRequired"]:
                    out.append({
                        "pointer": op["pointer"],
                        "path": loc,
                        "message": "Request body is present but not marked required: true",
                    })
    return out
