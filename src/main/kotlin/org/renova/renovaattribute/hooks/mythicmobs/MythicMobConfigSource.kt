package org.renova.renovaattribute.hooks.mythicmobs

import org.bukkit.entity.Entity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import java.util.UUID

class MythicMobConfigSource(
    private val entityId: UUID,
    private val values: Map<AttributeKey, StatValue>,
    override val priority: Int,
) : AttributeSource {
    override val id: String = ID

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> =
        if (entity.uniqueId == entityId) values else emptyMap()

    companion object {
        const val ID = "mythicmobs:config"
    }
}
