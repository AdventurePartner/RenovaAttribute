package org.renova.renovaattribute.api

import java.util.UUID

data class AttributeSnapshot(
    val entityId: UUID,
    val values: Map<AttributeKey, Double>,
    val createdAtNanos: Long = System.nanoTime(),
) {
    init {
        require(values.values.all(Double::isFinite)) { "Attribute snapshots require finite values" }
    }

    operator fun get(key: AttributeKey): Double = values[key] ?: 0.0

    fun has(key: AttributeKey): Boolean = values.containsKey(key)
}
