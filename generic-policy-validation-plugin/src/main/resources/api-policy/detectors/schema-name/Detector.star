_PASCAL_CASE = r"[A-Z][A-Za-z0-9]*"


def detect(api, rule):
    p = rule["parameters"]
    pattern = p["pattern"]
    require_pascal = p.get("case") == "pascal-case"
    out = []
    for schema in api["schemas"]:
        name = schema["name"]
        if not re_fullmatch(pattern, name):
            continue
        if require_pascal:
            if not re_fullmatch(_PASCAL_CASE, name):
                out.append({
                    "pointer": schema["pointer"],
                    "path": name,
                    "message": "Schema name '" + name + "' is a request/response object but is not PascalCase",
                })
        else:
            out.append({
                "pointer": schema["pointer"],
                "path": name,
                "message": "Schema name '" + name + "' is a placeholder rather than a meaningful domain name",
            })
    return out
