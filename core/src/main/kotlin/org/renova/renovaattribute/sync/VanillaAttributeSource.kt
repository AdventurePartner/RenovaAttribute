package org.renova.renovaattribute.sync

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.api.AttributeKey
import org.renova.renovaattribute.api.AttributeSource
import org.renova.renovaattribute.api.StatValue
import org.renova.renovaattribute.attribute.BuiltinAttributes
import org.renova.renovaattribute.platform.VanillaAttributes

object VanillaAttributeSource : AttributeSource {
    override val id: String = "renova:vanilla-baseline"
    override val priority: Int = Int.MIN_VALUE

    override fun provide(entity: Entity): Map<AttributeKey, StatValue> {
        if (entity !is LivingEntity) {
            return emptyMap()
        }
        return buildMap {
            VanillaAttributes.maxHealth(entity)?.baseValue?.let {
                put(BuiltinAttributes.MAX_HEALTH, StatValue.base(it))
            }
            VanillaAttributes.movementSpeed(entity)?.baseValue?.let {
                put(BuiltinAttributes.MOVEMENT_SPEED, StatValue.base(it))
            }
        }
    }
}
