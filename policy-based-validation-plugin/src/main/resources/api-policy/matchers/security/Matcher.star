# Port of Matcher.groovy (issue #125).

def _requirement_satisfied(requirement, scheme, required_scopes):
    if type(requirement) != "dict" or scheme not in requirement:
        return False
    if not required_scopes:
        return True
    granted = requirement[scheme]
    if type(granted) != "list":
        return False
    granted_text = [str(scope) for scope in granted]
    return all([scope in granted_text for scope in required_scopes])

def _effective_security_requires(security, scheme, required_scopes):
    if type(security) != "list" or len(security) == 0:
        return False
    return any([_requirement_satisfied(r, scheme, required_scopes) for r in security])

def detect(api, rule):
    p = rule["parameters"]
    scheme = p.get("scheme")
    raw_scopes = p.get("scopes") or ""
    required_scopes = [token.strip() for token in str(raw_scopes).split(",") if token.strip()]
    global_security = api["security"]

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            security = global_security if op["security"] == None else op["security"]
            if not _effective_security_requires(security, scheme, required_scopes):
                if not required_scopes:
                    message = "Operation does not require security scheme " + scheme
                else:
                    message = ("Operation does not require security scheme " + scheme
                               + " with scopes " + ", ".join(required_scopes))
                out.append({
                    "pointer": op["pointer"],
                    "path": op["method"] + " " + path["path"],
                    "message": message,
                })
    return out
