/*
 * Schema detector
 * ===============
 *
 * The detector receives `api.schemas[*].properties[*]`, a stable value map
 * made by the host. Each property has:
 *
 *   name, pointer, type, format, nullable, required, enumPresent
 *
 * Values are copies of OpenAPI facts, not parser-owned schema instances. This
 * avoids giving a bundle arbitrary Java objects and makes the detector API a
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
 * prove whether application-specific null semantics are defined. The detector
 * reports the mechanically observable condition and lets the rule text make
 * the limitation clear.
 *
 * A return value is a collection of occurrences. It never assigns points,
 * reads the active policy, or alters the API model. No matching properties
 * simply produce an empty collection.
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]
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
        if (parameters.format == 'present' && formatMissing) return false

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
        if (parameters['max-items'] == 'present' && property.maxItems == null) return false
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
        : parameters.format == 'absent' ? 'Numeric property does not declare a format'
        : 'Property matches the configured schema rule'

    api.schemas.collectMany { schema -> schema.properties ?: [] }
        .findAll(matches)
        .collect { property -> [pointer: property.pointer, path: property.name, message: message] }
}
