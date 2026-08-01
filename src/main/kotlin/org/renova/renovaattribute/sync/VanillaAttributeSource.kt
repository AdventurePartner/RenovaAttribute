package org.renova.renovaattribute.sync

import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.BuiltinAttributes

object VanillaAttributeSource : AttributeSource {
    override val id: String = "renova:vanilla-baseline"
    override val priority: Int = Int.MIN_VALUE

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> {
        if (entity !is LivingEntity) {
            return emptyMap()
        }
        return buildMap {
            entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue?.let {
                put(BuiltinAttributes.MAX_HEALTH, StatValue.base(it))
            }
            entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue?.let {
                put(BuiltinAttributes.MOVEMENT_SPEED, StatValue.base(it))
            }
        }
    }
}
