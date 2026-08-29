# Port of Detector.groovy (issue #125).
_UPPER_SNAKE = r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*"

def _enum_value_inconsistent(value, property_type):
    if property_type == "string":
        return type(value) != "string"
    if property_type == "integer":
        return type(value) != "int"
    if property_type == "number":
        return type(value) not in ("int", "float")
    return False

def _matches(prop, p):
    if p.get("type") and prop["type"] != p["type"]:
        return False

    format_absent = p.get("format") == "absent"
    not_numeric = prop["type"] not in ("integer", "number")
    format_present = p.get("format") == "present"
    format_missing = not prop["format"]
    if (format_absent and not_numeric) or (format_present and format_missing):
        return False

    if (p.get("enum-type") == "consistent" and prop["enumPresent"]
            and any([_enum_value_inconsistent(v, prop["type"]) for v in prop["enumValues"]])):
        return True
    if p.get("enum-type") == "consistent":
        return False

    if p.get("extensible") == "required" and prop["enumPresent"] and not prop["extensibleEnum"]:
        return True

    if p.get("enum-case") == "upper-snake-case" and any(
            [type(v) == "string" and not re_fullmatch(_UPPER_SNAKE, v) for v in prop["enumValues"]]):
        return True

    if p.get("max-items") == "absent" and prop["maxItems"] != None:
        return False
    if p.get("max-items") == "present" and prop["maxItems"] == None:
        return False
    if p.get("enum") == "present" and not prop["enumPresent"]:
        return False
    if p.get("enum") == "absent" and prop["enumPresent"]:
        return False
    if "nullable" in p and prop["nullable"] != p["nullable"]:
        return False
    if "required" in p and prop["required"] != p["required"]:
        return False
    return True

def _message(p):
    if p.get("enum") == "present":
        return "Property uses an enum"
    if p.get("enum") == "absent":
        return "Property does not use an enum"
    if "nullable" in p and p.get("required") == False:
        return "Optional property explicitly permits null"
    if p.get("max-items") == "absent":
        return "Array property has no maximum item count"
    return "Property matches the configured schema rule"

def detect(api, rule):
    p = rule["parameters"]
    message = _message(p)
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if _matches(prop, p):
                out.append({"pointer": prop["pointer"], "path": prop["name"], "message": message})
    return out
