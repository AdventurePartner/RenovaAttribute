package org.renova.renovaattribute.damage

import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.api.AttributeSnapshot

data class DamageContext(
    val attacker: LivingEntity,
    val defender: LivingEntity,
    val attackerAttributes: AttributeSnapshot,
    val defenderAttributes: AttributeSnapshot,
    val originalDamage: Double,
    val type: DamageType,
)
