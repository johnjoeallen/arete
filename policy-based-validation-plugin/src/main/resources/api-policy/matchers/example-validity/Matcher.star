def _number(value):
    return value if type(value) in ("int", "float") else None


def _covers_required(api):
    out = []
    for schema in api["schemas"]:
        example = schema.get("example")
        if type(example) != "dict":
            continue
        for field in schema.get("requiredFields", []):
            if field not in example:
                out.append({
                    "pointer": schema["pointer"],
                    "path": schema["name"],
                    "message": "Schema example omits the required field '" + field + "'",
                })
    return out


def _satisfies_constraints(api):
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if not prop.get("examplePresent"):
                continue
            example = prop.get("example")
            loc = schema["name"] + "." + prop["name"]
            pointer = prop["pointer"]

            pattern = prop.get("pattern")
            if pattern and type(example) == "string" and not re_search(pattern, example):
                out.append({"pointer": pointer, "path": loc, "message": "Example does not match pattern " + pattern})

            if type(example) == "string":
                min_length = prop.get("minLength")
                max_length = prop.get("maxLength")
                if min_length != None and len(example) < min_length:
                    out.append({"pointer": pointer, "path": loc, "message": "Example is shorter than minLength " + str(min_length)})
                if max_length != None and len(example) > max_length:
                    out.append({"pointer": pointer, "path": loc, "message": "Example is longer than maxLength " + str(max_length)})

            number = _number(example)
            if number != None:
                minimum = _number(prop.get("minimum"))
                maximum = _number(prop.get("maximum"))
                if minimum != None:
                    if prop.get("exclusiveMinimum") and number <= minimum:
                        out.append({"pointer": pointer, "path": loc, "message": "Example is not greater than exclusive minimum " + str(minimum)})
                    elif not prop.get("exclusiveMinimum") and number < minimum:
                        out.append({"pointer": pointer, "path": loc, "message": "Example is below minimum " + str(minimum)})
                if maximum != None:
                    if prop.get("exclusiveMaximum") and number >= maximum:
                        out.append({"pointer": pointer, "path": loc, "message": "Example is not less than exclusive maximum " + str(maximum)})
                    elif not prop.get("exclusiveMaximum") and number > maximum:
                        out.append({"pointer": pointer, "path": loc, "message": "Example exceeds maximum " + str(maximum)})

            if prop.get("enumPresent") and type(example) in ("string", "int", "float", "bool"):
                allowed = [str(value) for value in prop["enumValues"]]
                if str(example) not in allowed:
                    out.append({"pointer": pointer, "path": loc, "message": "Example is not one of the declared enum values"})
    return out


def detect(api, rule):
    check = rule["parameters"]["check"]
    if check == "covers-required":
        return _covers_required(api)
    if check == "satisfies-constraints":
        return _satisfies_constraints(api)
    return []
