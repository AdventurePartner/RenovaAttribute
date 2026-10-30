package org.renova.renovaattribute.damage

import org.bukkit.event.entity.EntityDamageEvent

object DamageTypeResolver {
    @Volatile
    private var physicalCauses = emptySet<EntityDamageEvent.DamageCause>()

    @Volatile
    private var magicCauses = emptySet<EntityDamageEvent.DamageCause>()

    fun configure(
        physicalCauses: Set<EntityDamageEvent.DamageCause>,
        magicCauses: Set<EntityDamageEvent.DamageCause>,
    ) {
        this.physicalCauses = physicalCauses.toSet()
        this.magicCauses = magicCauses.toSet()
    }

    fun resolve(cause: EntityDamageEvent.DamageCause): DamageType? = when (cause) {
        in physicalCauses -> DamageType.PHYSICAL
        in magicCauses -> DamageType.MAGIC
        else -> null
    }
}
