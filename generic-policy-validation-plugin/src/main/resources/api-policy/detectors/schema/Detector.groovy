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
    def matches = { property ->
        if (parameters.type && property.type != parameters.type) return false
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
        : 'Property matches the configured schema rule'

    api.schemas.collectMany { schema -> schema.properties ?: [] }
        .findAll(matches)
        .collect { property -> [pointer: property.pointer, path: property.name, message: message] }
}
