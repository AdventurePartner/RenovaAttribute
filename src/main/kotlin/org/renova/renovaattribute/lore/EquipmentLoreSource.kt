package org.renova.renovaattribute.lore

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue

object EquipmentLoreSource : AttributeSource {
    override val id: String = "renova:equipment-lore"
    override var priority: Int = 100
        private set

    var enabled: Boolean = true
        private set

    fun configure(enabled: Boolean, priority: Int) {
        this.enabled = enabled
        this.priority = priority
    }

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> {
        if (!enabled || entity !is LivingEntity) {
            return emptyMap()
        }
        val equipment = entity.equipment ?: return emptyMap()
        val items = buildList {
            add(equipment.itemInMainHand)
            add(equipment.itemInOffHand)
            equipment.armorContents.filterNotNullTo(this)
        }
        val result = linkedMapOf<AttributeKey, StatValue>()
        items.forEach { item ->
            LoreParser.parse(item).forEach { (key, contribution) ->
                result[key] = (result[key] ?: StatValue()).merge(contribution)
            }
        }
        return result
    }
}
