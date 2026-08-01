package org.renova.renovaattribute.source

import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.StatValue

data class TimedModification(
    val id: String,
    val tag: String,
    val key: AttributeKey,
    val value: StatValue,
    val expiresAtTick: Long,
    val sequence: Long,
) {
    fun expired(currentTick: Long): Boolean =
        expiresAtTick != Long.MAX_VALUE && currentTick >= expiresAtTick
}
