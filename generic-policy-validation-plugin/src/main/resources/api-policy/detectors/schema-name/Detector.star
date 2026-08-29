def detect(api, rule):
    pattern = rule["parameters"]["pattern"]
    out = []
    for schema in api["schemas"]:
        if re_fullmatch(pattern, schema["name"]):
            out.append({
                "pointer": schema["pointer"],
                "path": schema["name"],
                "message": "Schema name '" + schema["name"] + "' is a placeholder rather than a meaningful domain name",
            })
    return out
