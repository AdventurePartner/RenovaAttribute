package org.renova.renovaattribute.attribute

import org.renova.renovaattribute.api.AttributeKey

object BuiltinAttributes {
    @JvmField val MAX_HEALTH = AttributeKey.of("renova", "max-health")
    @JvmField val PHYSICAL_DAMAGE = AttributeKey.of("renova", "physical-damage")
    @JvmField val MAGIC_DAMAGE = AttributeKey.of("renova", "magic-damage")
    @JvmField val DAMAGE_BOOST = AttributeKey.of("renova", "damage-boost")
    @JvmField val PHYSICAL_DEFENSE = AttributeKey.of("renova", "physical-defense")
    @JvmField val MAGIC_DEFENSE = AttributeKey.of("renova", "magic-defense")
    @JvmField val TRUE_DAMAGE = AttributeKey.of("renova", "true-damage")
    @JvmField val CRITICAL_CHANCE = AttributeKey.of("renova", "critical-chance")
    @JvmField val CRITICAL_DAMAGE = AttributeKey.of("renova", "critical-damage")
    @JvmField val LIFESTEAL = AttributeKey.of("renova", "lifesteal")
    @JvmField val MOVEMENT_SPEED = AttributeKey.of("renova", "movement-speed")
    @JvmField val MAX_MANA = AttributeKey.of("renova", "max-mana")

    @JvmField
    val ALL = setOf(
        MAX_HEALTH,
        PHYSICAL_DAMAGE,
        MAGIC_DAMAGE,
        DAMAGE_BOOST,
        PHYSICAL_DEFENSE,
        MAGIC_DEFENSE,
        TRUE_DAMAGE,
        CRITICAL_CHANCE,
        CRITICAL_DAMAGE,
        LIFESTEAL,
        MOVEMENT_SPEED,
        MAX_MANA,
    )
}
