package org.renova.renovaattribute.formula

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue

data class FormulaContext(
    val contribution: StatValue,
    val values: Map<AttributeKey, Double>,
    val defaultNamespace: String,
) {
    fun get(key: String): Double = values[AttributeKey.parse(key, defaultNamespace)] ?: 0.0
}
