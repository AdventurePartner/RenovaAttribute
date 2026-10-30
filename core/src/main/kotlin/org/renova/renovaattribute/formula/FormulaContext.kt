package org.renova.renovaattribute.formula

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue

data class FormulaContext(
    val contribution: StatValue,
    val values: Map<AttributeKey, Double>,
    val defaultNamespace: String,
    val self: AttributeKey? = null,
    val dependencies: Set<AttributeKey> = emptySet(),
    val formulaAttributes: Set<AttributeKey> = emptySet(),
) {
    fun get(key: String): Double {
        val attributeKey = AttributeKeyCache.parse(key, defaultNamespace)
        val value = requireNotNull(values[attributeKey]) { "Unknown attribute $attributeKey" }
        // Formula attributes are only final after their own formula ran, which the dependency order guarantees.
        check(attributeKey == self || attributeKey !in formulaAttributes || attributeKey in dependencies) {
            "Formula attribute $attributeKey must be declared in depends-on"
        }
        return value
    }
}
