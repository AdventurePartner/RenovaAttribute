package org.renova.renovaattribute.api

import org.bukkit.entity.Entity

interface AttributeSource {
    val id: String
    val priority: Int

    fun provide(entity: Entity): Map<AttributeKey, StatValue>
}
