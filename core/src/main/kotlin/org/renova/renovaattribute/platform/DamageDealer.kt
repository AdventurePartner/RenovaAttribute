package org.renova.renovaattribute.platform

import org.bukkit.entity.LivingEntity

interface DamageDealer {
    val name: String

    /** Damages [victim] through vanilla armour and events, credited to [attacker] when given. */
    fun damage(victim: LivingEntity, amount: Double, attacker: LivingEntity?)
}

/** Before 1.20.4 there is no DamageSource API; the attacker overload still credits the kill. */
object LegacyDamageDealer : DamageDealer {
    override val name: String = "legacy"

    override fun damage(victim: LivingEntity, amount: Double, attacker: LivingEntity?) {
        if (attacker == null) {
            victim.damage(amount)
        } else {
            victim.damage(amount, attacker)
        }
    }
}
