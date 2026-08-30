def _blank(value):
    return value == None or (type(value) == "string" and value.strip() == "")


def _check(out, item, require, pointer, where):
    missing_description = require != "example" and _blank(item.get("description"))
    missing_example = require != "description" and not item.get("examplePresent")
    if missing_description and missing_example:
        out.append({"pointer": pointer, "path": where, "message": where + " has no description or example"})
    elif missing_description:
        out.append({"pointer": pointer, "path": where, "message": where + " has no description"})
    elif missing_example:
        out.append({"pointer": pointer, "path": where, "message": where + " has no example"})


def detect(api, rule):
    require = rule["parameters"]["require"]
    scope = rule["scope"]
    out = []
    if scope == "property":
        for schema in api["schemas"]:
            for prop in schema["properties"]:
                _check(out, prop, require, prop["pointer"], schema["name"] + "." + prop["name"])
    elif scope == "parameter":
        for path in api["paths"]:
            for op in path["operationDetails"]:
                for prm in op["parameters"]:
                    _check(out, prm, require, prm["pointer"], op["method"] + " " + path["path"] + " " + prm["name"])
    return out
