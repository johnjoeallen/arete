/*
 * Schema rule
 * ===============
 *
 * The rule receives `api.schemas[*].properties[*]`, a stable value map
 * made by the host. Each property has:
 *
 *   name, pointer, type, format, nullable, required, enumPresent
 *
 * Values are copies of OpenAPI facts, not parser-owned schema instances. This
 * avoids giving a bundle arbitrary Java objects and makes the rule API a
 * compatibility boundary for future parser upgrades.
 *
 * Supported configuration
 * -----------------------
 *
 * type: string|integer|number
 * enum: present|absent
 * nullable: true|false
 * required: true|false
 * semantics: undefined
 *
 * Every specified fact is ANDed. `semantics: undefined` is intentionally
 * documentary: an OpenAPI document can expose optional + nullable, but cannot
 * prove whether application-specific null semantics are defined. The rule
 * reports the mechanically observable condition and lets the rule text make
 * the limitation clear.
 *
 * A return value is a collection of diagnostics. It never assigns points,
 * reads the active policy, or alters the API model. No matching properties
 * simply produce an empty collection.
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]
    def checkKeys = ['format', 'enum', 'enum-type', 'enum-case', 'extensible',
                     'max-items', 'max-length', 'bounds', 'nullable', 'required']
    if (!checkKeys.any { parameters.containsKey(it) }) return []
    def enumInconsistent = { v, t ->
        t == 'string' ? !(v instanceof CharSequence)
            : t == 'integer' ? !(v instanceof Integer || v instanceof Long)
            : t == 'number' ? !(v instanceof Number)
            : false
    }
    def matches = { property ->
        if (parameters.type && property.type != parameters.type) return false

        boolean formatAbsent = parameters.format == 'absent'
        boolean notNumeric = !(property.type in ['integer', 'number'])
        boolean formatMissing = !property.format
        if (formatAbsent && (notNumeric || !formatMissing)) return false

        if (parameters['max-length'] == 'absent' && (property.type != 'string' || property.maxLength != null)) return false
        if (parameters.bounds == 'complete'
            && (notNumeric || (property.minimum != null && property.maximum != null))) return false

        if (parameters['enum-type'] == 'consistent') {
            return property.enumPresent && property.enumValues.any { v -> enumInconsistent(v, property.type) }
        }
        if (parameters.extensible == 'required') {
            return property.enumPresent && !property.extensibleEnum
        }
        if (parameters['enum-case'] == 'upper-snake-case') {
            return property.enumPresent && property.enumValues.any { v ->
                v instanceof CharSequence && !(v ==~ /[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*/)
            }
        }
        if (parameters['max-items'] == 'absent' && property.maxItems != null) return false
        if (parameters.enum == 'present' && !property.enumPresent) return false
        if (parameters.enum == 'absent' && property.enumPresent) return false
        if (parameters.containsKey('nullable') && property.nullable != parameters.nullable) return false
        if (parameters.containsKey('required') && property.required != parameters.required) return false
        true
    }
    def message = parameters.enum == 'present' ? 'Property uses an enum'
        : parameters.enum == 'absent' ? 'Property does not use an enum'
        : parameters.containsKey('nullable') && parameters.required == false ? 'Optional property explicitly permits null'
        : parameters['max-items'] == 'absent' ? 'Array property has no maximum item count'
        : parameters['max-length'] == 'absent' ? 'String property does not declare a maximum length'
        : parameters.bounds == 'complete' ? 'Numeric property does not declare both a minimum and a maximum'
        : parameters.format == 'absent' ? 'Numeric property does not declare a format'
        : parameters['enum-type'] == 'consistent' ? 'Enum value type is inconsistent with the property type'
        : parameters['enum-case'] == 'upper-snake-case' ? 'Enum value is not UPPER_SNAKE_CASE'
        : parameters.extensible == 'required' ? 'Enum is not marked extensible'
        : 'Property matches the flagged nullable/required condition'

    api.schemas.collectMany { schema -> schema.properties ?: [] }
        .findAll(matches)
        .collect { property -> [pointer: property.pointer, path: property.name, message: message] }
}
