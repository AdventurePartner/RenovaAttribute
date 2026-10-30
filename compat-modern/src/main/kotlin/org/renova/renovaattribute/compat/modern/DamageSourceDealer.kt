package org.renova.renovaattribute.compat.modern

import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.LivingEntity
import org.renova.renovaattribute.platform.DamageDealer

/** 1.20.4+: generic damage that still names the attacker, instead of a melee hit from it. */
class DamageSourceDealer : DamageDealer {
    override val name: String = "damage-source"

    override fun damage(victim: LivingEntity, amount: Double, attacker: LivingEntity?) {
        val source = DamageSource.builder(DamageType.GENERIC).apply {
            if (attacker != null) {
                withCausingEntity(attacker)
                withDirectEntity(attacker)
            }
        }.build()
        victim.damage(amount, source)
    }
}
