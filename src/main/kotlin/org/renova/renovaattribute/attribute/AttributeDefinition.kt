package org.renova.renovaattribute.attribute

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.ModifierMode

data class AttributeDefinition(
    val key: AttributeKey,
    val displayName: String,
    val category: AttributeCategory,
    val defaultValue: Double,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val format: AttributeFormat = AttributeFormat.NUMBER,
    val vanillaAttribute: String? = null,
    val loreMode: ModifierMode = ModifierMode.FLAT,
    val lorePercentValue: Boolean = false,
    val formula: String? = null,
    val dependencies: Set<AttributeKey> = emptySet(),
) {
    init {
        require(displayName.isNotBlank()) { "displayName cannot be blank for $key" }
        require(defaultValue.isFinite()) { "defaultValue must be finite for $key" }
        require(minValue == null || minValue.isFinite()) { "minValue must be finite for $key" }
        require(maxValue == null || maxValue.isFinite()) { "maxValue must be finite for $key" }
        require(minValue == null || maxValue == null || minValue <= maxValue) {
            "minValue must not exceed maxValue for $key"
        }
    }

    fun clamp(value: Double): Double {
        var result = value
        minValue?.let { result = result.coerceAtLeast(it) }
        maxValue?.let { result = result.coerceAtMost(it) }
        return result
    }

    fun format(value: Double): String = when (format) {
        AttributeFormat.NUMBER -> DECIMAL_FORMAT.format(value)
        AttributeFormat.PERCENT -> "${DECIMAL_FORMAT.format(value * 100.0)}%"
    }

    companion object {
        private val DECIMAL_FORMAT = java.text.DecimalFormat("0.##")
    }
}
